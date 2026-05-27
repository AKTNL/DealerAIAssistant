import { describe, expect, it, vi } from "vitest";
import { useSseParser } from "../useSseParser";

const encoder = new TextEncoder();

function streamFromText(text) {
  let consumed = false;

  return {
    getReader: () => ({
      read: vi.fn().mockImplementation(async () => {
        if (consumed) {
          return { done: true };
        }

        consumed = true;
        return {
          done: false,
          value: encoder.encode(text)
        };
      })
    })
  };
}

describe("useSseParser", () => {
  it("parses a single message event", async () => {
    const onEvent = vi.fn();

    await useSseParser().consume(streamFromText("data: hello\n\n"), onEvent);

    expect(onEvent).toHaveBeenCalledWith({
      event: "message",
      data: "hello"
    });
  });

  it("joins multi-line event data with newlines", async () => {
    const onEvent = vi.fn();

    await useSseParser().consume(
      streamFromText("event: progress\ndata: first line\ndata: second line\n\n"),
      onEvent
    );

    expect(onEvent).toHaveBeenCalledWith({
      event: "progress",
      data: "first line\nsecond line"
    });
  });

  it("parses JSON event data", async () => {
    const onEvent = vi.fn();

    await useSseParser().consume(
      streamFromText('event: message\ndata: {"count":2,"items":["D001"]}\n\n'),
      onEvent
    );

    expect(onEvent).toHaveBeenCalledWith({
      event: "message",
      data: {
        count: 2,
        items: ["D001"]
      }
    });
  });

  it("returns non-JSON data unchanged", async () => {
    const onEvent = vi.fn();

    await useSseParser().consume(streamFromText("event: note\ndata: not-json\n\n"), onEvent);

    expect(onEvent).toHaveBeenCalledWith({
      event: "note",
      data: "not-json"
    });
  });

  it("cancels the reader and rethrows when a stream read fails", async () => {
    const readError = new Error("read failed");
    const reader = {
      cancel: vi.fn().mockResolvedValue(undefined),
      read: vi.fn().mockRejectedValue(readError)
    };
    const stream = {
      getReader: () => reader
    };
    const onEvent = vi.fn();

    await expect(useSseParser().consume(stream, onEvent)).rejects.toBe(readError);

    expect(reader.cancel).toHaveBeenCalledTimes(1);
    expect(onEvent).not.toHaveBeenCalled();
  });
});
