
export default async (request) => {
  if (request.method !== "POST") {
    return Response.json({ error: "POST requests only" }, { status: 405 });
  }

  const geminiKey = process.env.GEMINI_API_KEY;
  const groqKey = process.env.GROQ_API_KEY;

  if (!geminiKey && !groqKey) {
    return Response.json({ error: "No AI provider configured" }, { status: 500 });
  }

  try {
    const body = await request.json();
    const messages = body.messages;

    if (!Array.isArray(messages) || messages.length === 0) {
      return Response.json({ error: "Messages are required" }, { status: 400 });
    }

    const systemPrompt = `You are D.N. MARK 1, a helpful personal AI assistant.
Understand and respond naturally in Hindi, Odia, and English.
Use the same language as the user.
Be clear, accurate, and helpful.
Never claim a phone action was completed unless Android confirms it.
Never request passwords or API keys.
Ask confirmation before important or irreversible actions.`;

    const history = messages.slice(-20).map(item => ({
      role: item.role === "assistant" ? "model" : "user",
      parts: [{ text: String(item.content || "").slice(0, 4000) }]
    }));

    // 1. Try Gemini first
    if (geminiKey) {
      let response = await fetch(
        "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.7-flash:generateContent",
        {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            "x-goog-api-key": geminiKey
          },
          body: JSON.stringify({
            systemInstruction: { parts: [{ text: systemPrompt }] },
            contents: history,
            generationConfig: {
              temperature: 0.7,
              maxOutputTokens: 2048
            }
          })
        }
      );

      let data = await response.json().catch(() => ({}));

      if (response.ok) {
        const answer = data.candidates?.[0]?.content?.parts
          ?.map(part => part.text || "").join("").trim();

        if (answer) return Response.json({ reply: answer, provider: "Gemini" });
      }

      const retryable = [408, 429, 500, 502, 503, 504].includes(response.status);

      if (retryable) {
        await new Promise(resolve => setTimeout(resolve, 700));

        response = await fetch(
          "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.7-flash:generateContent",
          {
            method: "POST",
            headers: {
              "Content-Type": "application/json",
              "x-goog-api-key": geminiKey
            },
            body: JSON.stringify({
              systemInstruction: { parts: [{ text: systemPrompt }] },
              contents: history,
              generationConfig: {
                temperature: 0.7,
                maxOutputTokens: 2048
              }
            })
          }
        );

        data = await response.json().catch(() => ({}));

        if (response.ok) {
          const answer = data.candidates?.[0]?.content?.parts
            ?.map(part => part.text || "").join("").trim();

          if (answer) return Response.json({ reply: answer, provider: "Gemini" });
        }
      }

      if (!retryable) {
        return Response.json({
          error: "Gemini request failed",
          details: data.error?.message || "Unknown error"
        }, { status: 502 });
      }
    }

    // 2. Gemini unavailable: use Groq
    if (groqKey) {
      const groqMessages = [
        { role: "system", content: systemPrompt },
        ...messages.slice(-20).map(item => ({
          role: item.role === "assistant" ? "assistant" : "user",
          content: String(item.content || "").slice(0, 4000)
        }))
      ];

      const response = await fetch(
        "https://api.groq.com/openai/v1/chat/completions",
        {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            "Authorization": `Bearer ${groqKey}`
          },
          body: JSON.stringify({
            model: "openai/gpt-oss-120b",
            messages: groqMessages,
            temperature: 0.7,
            max_completion_tokens: 2048
          })
        }
      );

      const data = await response.json().catch(() => ({}));
      const answer = data.choices?.[0]?.message?.content?.trim();

      if (response.ok && answer) {
        return Response.json({ reply: answer, provider: "Groq" });
      }

      return Response.json({
        error: "Both AI providers failed",
        details: data.error?.message || "Groq request failed"
      }, { status: 502 });
    }

    return Response.json({
      error: "Gemini unavailable and Groq backup is not configured"
    }, { status: 502 });

  } catch (error) {
    return Response.json({
      error: "Backend request failed",
      details: error.message
    }, { status: 500 });
  }
};
