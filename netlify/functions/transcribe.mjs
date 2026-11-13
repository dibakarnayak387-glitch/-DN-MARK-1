export default async (request) => {
  if (request.method !== "POST") {
    return Response.json({ error: "POST only" }, { status: 405 });
  }

  const apiKey = process.env.CHARIOT_API_KEY;

  if (!apiKey) {
    return Response.json(
      { error: "Chariot API key is not configured" },
      { status: 503 }
    );
  }

  try {
    const incoming = await request.formData();
    const audio = incoming.get("audio");

    if (!audio || typeof audio.arrayBuffer !== "function") {
      return Response.json(
        { error: "Audio file is required" },
        { status: 400 }
      );
    }

    if (audio.size === 0 || audio.size > 500000) {
      return Response.json(
        { error: "Audio must be between 1 byte and 500 KB" },
        { status: 413 }
      );
    }

    const form = new FormData();
    form.append("input_audio_file", audio, "command.wav");
    form.append("style", "codemix");

    const response = await fetch(
      "https://api.chariot.in/v1/asr/transcribe",
      {
        method: "POST",
        headers: {
          "chariotai-api-key": apiKey
        },
        body: form,
        signal: AbortSignal.timeout(25000)
      }
    );

    const data = await response.json().catch(() => ({}));

    if (!response.ok) {
      return Response.json(
        { error: "Chariot transcription failed", details: data },
        { status: response.status }
      );
    }

    return Response.json({
      text: data.text || "",
      duration: data.duration || 0
    });

  } catch (error) {
    return Response.json(
      { error: "Transcription request failed" },
      { status: 500 }
    );
  }
};
