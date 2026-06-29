import React, { forwardRef, useEffect, useMemo, useState } from "react";

type SharedTextValue = string | number | readonly string[] | undefined;

type SafeInputProps = Omit<React.InputHTMLAttributes<HTMLInputElement>, "value" | "onChange"> & {
  value: SharedTextValue;
  onValueChange: (value: string) => void;
};

type SafeTextareaProps = Omit<React.TextareaHTMLAttributes<HTMLTextAreaElement>, "value" | "onChange"> & {
  value: SharedTextValue;
  onValueChange: (value: string) => void;
};

function normalizeValue(value: SharedTextValue) {
  if (Array.isArray(value)) {
    return value.join(",");
  }
  return value == null ? "" : String(value);
}

export const SafeInput = forwardRef<HTMLInputElement, SafeInputProps>(function SafeInput(
  { value, onValueChange, onCompositionStart, onCompositionEnd, ...props },
  ref
) {
  const [isComposing, setIsComposing] = useState(false);
  const [composingValue, setComposingValue] = useState("");
  const normalizedValue = useMemo(() => normalizeValue(value), [value]);

  useEffect(() => {
    if (!isComposing) {
      setComposingValue(normalizedValue);
    }
  }, [isComposing, normalizedValue]);

  return (
    <input
      {...props}
      ref={ref}
      value={isComposing ? composingValue : normalizedValue}
      onChange={(event) => {
        const nextValue = event.target.value;
        if (isComposing) {
          setComposingValue(nextValue);
          return;
        }
        onValueChange(nextValue);
      }}
      onCompositionStart={(event) => {
        setIsComposing(true);
        setComposingValue(event.currentTarget.value);
        onCompositionStart?.(event);
      }}
      onCompositionEnd={(event) => {
        const nextValue = event.currentTarget.value;
        setIsComposing(false);
        setComposingValue(nextValue);
        onValueChange(nextValue);
        onCompositionEnd?.(event);
      }}
    />
  );
});

export const SafeTextarea = forwardRef<HTMLTextAreaElement, SafeTextareaProps>(function SafeTextarea(
  { value, onValueChange, onCompositionStart, onCompositionEnd, ...props },
  ref
) {
  const [isComposing, setIsComposing] = useState(false);
  const [composingValue, setComposingValue] = useState("");
  const normalizedValue = useMemo(() => normalizeValue(value), [value]);

  useEffect(() => {
    if (!isComposing) {
      setComposingValue(normalizedValue);
    }
  }, [isComposing, normalizedValue]);

  return (
    <textarea
      {...props}
      ref={ref}
      value={isComposing ? composingValue : normalizedValue}
      onChange={(event) => {
        const nextValue = event.target.value;
        if (isComposing) {
          setComposingValue(nextValue);
          return;
        }
        onValueChange(nextValue);
      }}
      onCompositionStart={(event) => {
        setIsComposing(true);
        setComposingValue(event.currentTarget.value);
        onCompositionStart?.(event);
      }}
      onCompositionEnd={(event) => {
        const nextValue = event.currentTarget.value;
        setIsComposing(false);
        setComposingValue(nextValue);
        onValueChange(nextValue);
        onCompositionEnd?.(event);
      }}
    />
  );
});
