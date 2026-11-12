
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

    const systemPrompt = `You are D.N. MARK 1, a helpful personal AI assistant.
Understand Hindi, Odia and English.
Reply in the user's language.
Be clear and helpful.
Never claim a phone action was completed unless Android confirms it.
Ask confirmation before important or irreversible actions.`;

    const history = messages.slice(-12).map(item => ({
      role: item.role === "assistant" ? "model" : "user",
      parts: [{ text: String(item.content || "").slice(0, 3000) }]
    }));

    // Try Gemini for maximum 4 seconds
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
                temperature: 0.7,
                maxOutputTokens: 1024
              }
            })
          }
        );

        const data = await response.json().catch(() => ({}));
        const answer = data.candidates?.[0]?.content?.parts
          ?.map(p => p.text || "").join("").trim();

        if (response.ok && answer) {
          return Response.json({ reply: answer, provider: "Gemini" });
        }
      } catch (error) {
        // Gemini timeout or connection error: try Groq
      } finally {
        clearTimeout(timer);
      }
    }

    // Backup: Groq, maximum 5 seconds
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
              temperature: 0.7,
              max_completion_tokens: 1024
            })
          }
        );

        const data = await response.json().catch(() => ({}));
        const answer = data.choices?.[0]?.message?.content?.trim();

        if (response.ok && answer) {
          return Response.json({ reply: answer, provider: "Groq" });
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

    return Response.json({
      error: "No AI provider available"
    }, { status: 503 });

  } catch (error) {
    return Response.json({
      error: "Backend error"
    }, { status: 500 });
  }
};
