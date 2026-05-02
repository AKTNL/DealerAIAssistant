export function useSseParser() {
  async function consume(stream, onEvent) {
    const reader = stream.getReader();
    const decoder = new TextDecoder();
    let buffer = "";

    while (true) {
      const { done, value } = await reader.read();
      buffer += decoder.decode(value ?? new Uint8Array(), { stream: !done });

      let separatorMatch = buffer.match(/\r?\n\r?\n/);
      while (separatorMatch) {
        const separatorIndex = separatorMatch.index ?? -1;
        if (separatorIndex < 0) {
          break;
        }

        const rawEvent = buffer.slice(0, separatorIndex);
        buffer = buffer.slice(separatorIndex + separatorMatch[0].length);

        if (rawEvent.trim()) {
          const parsed = parseSseEvent(rawEvent);
          if (parsed) {
            onEvent(parsed);
          }
        }

        separatorMatch = buffer.match(/\r?\n\r?\n/);
      }

      if (done) {
        if (buffer.trim()) {
          const parsed = parseSseEvent(buffer);
          if (parsed) {
            onEvent(parsed);
          }
        }
        break;
      }
    }
  }

  return {
    consume
  };
}

function parseSseEvent(chunk) {
  const lines = chunk.split(/\r?\n/);
  let event = "message";
  const dataLines = [];

  for (const line of lines) {
    if (line.startsWith("event:")) {
      event = line.slice(6).trim();
    } else if (line.startsWith("data:")) {
      dataLines.push(line.slice(5).replace(/^ /, ""));
    }
  }

  return {
    event,
    data: dataLines.join("\n")
  };
}
