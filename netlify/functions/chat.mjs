
export default async (request) => {
  if (request.method !== "POST") {
    return Response.json(
      { error: "POST requests only" },
      { status: 405 }
    );
  }

  const apiKey = process.env.GEMINI_API_KEY;

  if (!apiKey) {
    return Response.json(
      { error: "Gemini API key is not configured" },
      { status: 500 }
    );
  }

  try {
    const body = await request.json();
    const messages = body.messages;

    if (!Array.isArray(messages) || messages.length === 0) {
      return Response.json(
        { error: "Messages are required" },
        { status: 400 }
      );
    }

    const contents = messages.slice(-20).map((item) => {
      const role = item.role === "assistant" ? "model" : "user";

      return {
        role,
        parts: [{ text: String(item.content || "").slice(0, 4000) }]
      };
    });

    const response = await fetch(
      "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.7-flash:generateContent",
      {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "x-goog-api-key": apiKey
        },
        body: JSON.stringify({
          systemInstruction: {
            parts: [{
              text: `You are D.N. MARK 1, a helpful personal AI assistant.
Understand and respond naturally in Hindi, Odia, and English.
Use the same language as the user.
Be clear, accurate, and helpful.
You may plan complex tasks step by step.
Never claim that a phone action was completed unless the Android app confirms it.
Never request passwords, API keys, or private credentials.
Ask for user confirmation before important or irreversible actions.`
            }]
          },
          contents,
          generationConfig: {
            temperature: 0.7,
            maxOutputTokens: 2048
          }
        })
      }
    );

    const data = await response.json();

    if (!response.ok) {
      return Response.json(
        { error: "Gemini request failed", details: data.error?.message || "Unknown error" },
        { status: 502 }
      );
    }

    const answer =
      data.candidates?.[0]?.content?.parts
        ?.map((part) => part.text || "")
        .join("")
        .trim();

    if (!answer) {
      return Response.json(
        { error: "Gemini returned an empty response" },
        { status: 502 }
      );
    }

    return Response.json({ reply: answer });

  } catch (error) {
    return Response.json(
      { error: "Backend request failed" },
      { status: 500 }
    );
  }
};
