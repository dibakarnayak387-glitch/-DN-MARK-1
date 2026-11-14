
function parseAgentOutput(raw) {
  try {
    const cleaned = raw.replace(/^```(?:json)?\s*/i, "").replace(/\s*```$/, "");
    const parsed = JSON.parse(cleaned);

    return {
      reply: String(parsed.reply || "मैं आपकी मदद के लिए तैयार हूँ।"),
      agentPlan: {
        goal: String(parsed.goal || ""),
        steps: Array.isArray(parsed.steps)
          ? parsed.steps.slice(0, 5).map(step => ({
              action: ["answer_only", "open_app", "draft_message", "unsupported"].includes(step.action)
                ? step.action : "unsupported",
              target: String(step.target || "").slice(0, 100),
              description: String(step.description || "").slice(0, 200)
            }))
          : [],
        requiresConfirmation: Boolean(parsed.requiresConfirmation),
        confirmationReason: String(parsed.confirmationReason || "")
      }
    };
  } catch {
    return {
      reply: raw,
      agentPlan: {
        goal: "",
        steps: [{ action: "answer_only", target: "", description: "सामान्य जवाब" }],
        requiresConfirmation: false,
        confirmationReason: ""
      }
    };
  }
}

export default async (request) => {
  if (request.method !== "POST") {
    return Response.json({ error: "POST only" }, { status: 405 });
  }

  const geminiKey = process.env.GEMINI_API_KEY;
  const groqKey = process.env.GROQ_API_KEY;

  try {
    const body = await request.json();
    const messages = body.messages;

    if (!Array.isArray(messages) || !messages.length) {
      return Response.json({ error: "Messages required" }, { status: 400 });
    }

    const systemPrompt = `You are D.N. MARK 1, an advanced personal AI agent.
Understand Hindi, Odia and English. Reply in the user's language.

You must return ONLY valid JSON in this exact structure:
{
 "reply": "Natural spoken response for the user",
 "goal": "Short description of the user's goal",
 "steps": [
   {
     "action": "answer_only",
     "target": "",
     "description": "What this step means"
   }
 ],
 "requiresConfirmation": false,
 "confirmationReason": ""
}

 ["answer_only", "open_app", "youtube_search", "draft_message", "unsupported"].includes(step.action)
Rules:
1. Break multi-step tasks into a maximum of 5 clear steps.
2. Never generate executable code, shell commands, arbitrary intents or URLs as actions.
3. Never claim an action was completed unless Android confirms it.
4. Sending messages, deleting data, purchases and important changes require explicit user confirmation.
5. For draft_message, requiresConfirmation must be true.
6. If a task is unclear, ask the user a question in reply and use answer_only.
7. If a task cannot safely be performed, explain that in reply and use unsupported.
8. Do not invent Android permissions or device capabilities.
9. Keep the reply short and natural for voice playback.`;

    const history = messages.slice(-12).map(item => ({
      role: item.role === "assistant" ? "model" : "user",
      parts: [{ text: String(item.content || "").slice(0, 3000) }]
    }));

    if (geminiKey) {
      const controller = new AbortController();
      const timer = setTimeout(() => controller.abort(), 4000);

      try {
        const response = await fetch(
          "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.7-flash:generateContent",
          {
            method: "POST",
            signal: controller.signal,
            headers: {
              "Content-Type": "application/json",
              "x-goog-api-key": geminiKey
            },
            body: JSON.stringify({
              systemInstruction: { parts: [{ text: systemPrompt }] },
              contents: history,
              generationConfig: {
                temperature: 0.4,
                maxOutputTokens: 1200,
                responseMimeType: "application/json"
              }
            })
          }
        );

        const data = await response.json().catch(() => ({}));
        const raw = data.candidates?.[0]?.content?.parts
          ?.map(p => p.text || "").join("").trim();

        if (response.ok && raw) {
          const result = parseAgentOutput(raw);
          return Response.json({
            ...result,
            provider: "Gemini",
            agentMode: true
          });
        }
      } catch (error) {
        // Gemini unavailable: try backup provider.
      } finally {
        clearTimeout(timer);
      }
    }

    if (groqKey) {
      const controller = new AbortController();
      const timer = setTimeout(() => controller.abort(), 5000);

      try {
        const groqMessages = [
          { role: "system", content: systemPrompt },
          ...messages.slice(-12).map(item => ({
            role: item.role === "assistant" ? "assistant" : "user",
            content: String(item.content || "").slice(0, 3000)
          }))
        ];

        const response = await fetch(
          "https://api.groq.com/openai/v1/chat/completions",
          {
            method: "POST",
            signal: controller.signal,
            headers: {
              "Content-Type": "application/json",
              "Authorization": `Bearer ${groqKey}`
            },
            body: JSON.stringify({
              model: "openai/gpt-oss-120b",
              messages: groqMessages,
              temperature: 0.4,
              max_completion_tokens: 1200,
              response_format: { type: "json_object" }
            })
          }
        );

        const data = await response.json().catch(() => ({}));
        const raw = data.choices?.[0]?.message?.content?.trim();

        if (response.ok && raw) {
          const result = parseAgentOutput(raw);
          return Response.json({
            ...result,
            provider: "Groq",
            agentMode: true
          });
        }

        return Response.json({
          error: "Groq request failed",
          details: data.error?.message || "No response"
        }, { status: 502 });
      } catch (error) {
        return Response.json({
          error: "Groq timeout or connection failure"
        }, { status: 504 });
      } finally {
        clearTimeout(timer);
      }
    }

    return Response.json({ error: "No AI provider available" }, { status: 503 });
  } catch (error) {
    return Response.json({ error: "Backend error" }, { status: 500 });
  }
};
