import React, { useEffect, useState } from "react";
import { fetchRemarkSuggestions } from "../api/http";
import type { RemarkSuggestionScene } from "../api/types";
import { shouldLoadRemarkSuggestions } from "./remarkField.helpers";
import { SafeInput, SafeTextarea } from "./SafeInput";

type RemarkFieldProps = {
  label: string;
  value: string;
  onChange: (value: string) => void;
  scene: RemarkSuggestionScene;
  placeholder?: string;
  required?: boolean;
  multiline?: boolean;
  rows?: number;
  disabled?: boolean;
  customerId?: number | null;
};

export function RemarkField({
  label,
  value,
  onChange,
  scene,
  placeholder,
  required = false,
  multiline = false,
  rows = 3,
  disabled = false,
  customerId = null
}: RemarkFieldProps) {
  const [suggestions, setSuggestions] = useState<string[]>([]);
  const [showDropdown, setShowDropdown] = useState(false);

  useEffect(() => {
    if (!shouldLoadRemarkSuggestions(scene, customerId)) {
      setSuggestions([]);
      return;
    }

    let active = true;
    fetchRemarkSuggestions(scene, customerId)
      .then((response) => {
        if (!active) return;
        setSuggestions(response.items);
      })
      .catch((err) => {
        if (!active) return;
        console.error('加载备注意见失败', err);
        setSuggestions([]);
      });
    return () => {
      active = false;
    };
  }, [scene, customerId]);

  const handleSelectSuggestion = (item: string) => {
    onChange(item);
    setShowDropdown(false);
  };

  return (
    <div className="form-group">
      <label className="form-label">
        {required && <span className="required">*</span>}
        {label}
      </label>
      <div className="remark-field-wrapper">
        {multiline ? (
          <SafeTextarea
            className="form-control"
            rows={rows}
            value={value}
            onValueChange={onChange}
            onFocus={() => suggestions.length > 0 && setShowDropdown(true)}
            placeholder={placeholder}
            disabled={disabled}
          />
        ) : (
          <SafeInput
            className="form-control"
            value={value}
            onValueChange={onChange}
            onFocus={() => suggestions.length > 0 && setShowDropdown(true)}
            placeholder={placeholder}
            disabled={disabled}
          />
        )}
        {suggestions.length > 0 && showDropdown && !disabled && (
          <>
            <div className="remark-dropdown-backdrop" onClick={() => setShowDropdown(false)} />
            <div className="remark-dropdown">
              <div className="remark-dropdown-header">
                <span>常用备注</span>
                <button
                  type="button"
                  className="remark-dropdown-close"
                  onClick={() => setShowDropdown(false)}
                >
                  ×
                </button>
              </div>
              <div className="remark-dropdown-list">
                {suggestions.map((item) => (
                  <button
                    key={item}
                    type="button"
                    className={`remark-dropdown-item ${value === item ? "is-active" : ""}`}
                    onClick={() => handleSelectSuggestion(item)}
                  >
                    {item}
                  </button>
                ))}
              </div>
            </div>
          </>
        )}
      </div>
    </div>
  );
}
