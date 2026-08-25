import type { CSSProperties, KeyboardEvent } from "react";
import { useEffect, useLayoutEffect, useMemo, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { Check, ChevronDown, Search } from "lucide-react";

export type AppSelectOption = {
  label: string;
  value: string;
  disabled?: boolean;
};

type AppSelectProps = {
  value?: string;
  options: AppSelectOption[];
  placeholder?: string;
  className?: string;
  popupClassName?: string;
  style?: CSSProperties;
  showSearch?: boolean;
  disabled?: boolean;
  onChange?: (value: string) => void;
};

function joinClassNames(...values: Array<string | undefined | false>) {
  return values.filter(Boolean).join(" ");
}

function findNextEnabledIndex(start: number, list: AppSelectOption[], step: 1 | -1) {
  let index = start;
  for (let i = 0; i < list.length; i++) {
    index = (index + step + list.length) % list.length;
    if (!list[index]?.disabled) return index;
  }
  return -1;
}

/**
 * 自研下拉选择组件，替代 antd Select。
 * 保持与原 antd 包装版本完全一致的 props API（value/options/placeholder/className/
 * popupClassName/style/showSearch/disabled/onChange），因此 46 处调用方零改动。
 * 下拉菜单使用 createPortal 渲染到 document.body，避免被父容器 overflow 裁剪。
 * 主题色沿用 --primary-color（#2563eb）。
 */
export function AppSelect({
  value,
  options,
  placeholder,
  className,
  popupClassName,
  style,
  showSearch = false,
  disabled = false,
  onChange
}: AppSelectProps) {
  const [open, setOpen] = useState(false);
  const [searchQuery, setSearchQuery] = useState("");
  const [focusedIndex, setFocusedIndex] = useState(0);
  const [dropdownStyle, setDropdownStyle] = useState<CSSProperties>({});
  const containerRef = useRef<HTMLDivElement>(null);
  const menuRef = useRef<HTMLDivElement>(null);
  const searchInputRef = useRef<HTMLInputElement>(null);
  const optionRefs = useRef<Array<HTMLButtonElement | null>>([]);

  const selectedOption = options.find((option) => option.value === value);

  const filteredOptions = useMemo(() => {
    if (!showSearch || !searchQuery.trim()) {
      return options;
    }
    const query = searchQuery.toLowerCase();
    return options.filter((option) => option.label.toLowerCase().includes(query));
  }, [options, showSearch, searchQuery]);

  useLayoutEffect(() => {
    if (!open) return;
    function positionDropdown() {
      const rect = containerRef.current?.getBoundingClientRect();
      if (!rect) return;
      const menuHeight = menuRef.current?.offsetHeight ?? 280;
      const spaceBelow = window.innerHeight - rect.bottom - 12;
      const spaceAbove = rect.top - 12;
      const shouldPlaceAbove = spaceBelow < menuHeight && spaceAbove > spaceBelow;
      const top = shouldPlaceAbove
        ? rect.top + window.scrollY - (menuRef.current?.offsetHeight ?? 0)
        : rect.bottom + window.scrollY;
      setDropdownStyle({
        position: "fixed",
        top,
        left: rect.left + window.scrollX,
        width: rect.width,
        zIndex: 2000,
      });
    }
    positionDropdown();
    window.addEventListener("resize", positionDropdown);
    window.addEventListener("scroll", positionDropdown, true);
    const id = window.setInterval(positionDropdown, 200);
    return () => {
      window.removeEventListener("resize", positionDropdown);
      window.removeEventListener("scroll", positionDropdown, true);
      window.clearInterval(id);
    };
  }, [open]);

  useEffect(() => {
    if (!open) {
      setSearchQuery("");
      return undefined;
    }
    const selectedIndex = options.findIndex((option) => option.value === value);
    setFocusedIndex(selectedIndex >= 0 ? selectedIndex : 0);
    if (showSearch) {
      const timer = setTimeout(() => searchInputRef.current?.focus(), 0);
      return () => clearTimeout(timer);
    }
    return undefined;
  }, [open, options, value, showSearch]);

  useEffect(() => {
    if (!open) return;
    function handleClickOutside(event: PointerEvent) {
      const target = event.target as Node;
      const insideTrigger = containerRef.current?.contains(target);
      const insideMenu = menuRef.current?.contains(target);
      if (!insideTrigger && !insideMenu) {
        setOpen(false);
      }
    }
    function handleKeyDown(event: globalThis.KeyboardEvent) {
      if (event.key === "Escape") {
        setOpen(false);
      }
    }
    document.addEventListener("pointerdown", handleClickOutside);
    document.addEventListener("keydown", handleKeyDown);
    return () => {
      document.removeEventListener("pointerdown", handleClickOutside);
      document.removeEventListener("keydown", handleKeyDown);
    };
  }, [open]);

  useEffect(() => {
    if (open) {
      const focusedEl = optionRefs.current[focusedIndex];
      focusedEl?.scrollIntoView?.({ block: "nearest" });
    }
  }, [focusedIndex, open]);

  function handleOptionSelect(option: AppSelectOption) {
    if (option.disabled) return;
    onChange?.(option.value);
    setOpen(false);
  }

  function handleTriggerKeyDown(event: KeyboardEvent<HTMLButtonElement>) {
    if (disabled) return;
    if (event.key === "Enter" || event.key === " " || event.key === "ArrowDown" || event.key === "ArrowUp") {
      event.preventDefault();
      setOpen(true);
    }
  }

  function handleSearchKeyDown(event: KeyboardEvent<HTMLInputElement>) {
    if (event.key === "ArrowDown") {
      event.preventDefault();
      const next = findNextEnabledIndex(focusedIndex, filteredOptions, 1);
      if (next >= 0) setFocusedIndex(next);
    } else if (event.key === "ArrowUp") {
      event.preventDefault();
      const prev = findNextEnabledIndex(focusedIndex, filteredOptions, -1);
      if (prev >= 0) setFocusedIndex(prev);
    } else if (event.key === "Enter") {
      event.preventDefault();
      const option = filteredOptions[focusedIndex];
      if (option && !option.disabled) handleOptionSelect(option);
    }
  }

  const dropdown = open ? (
    <div
      ref={menuRef}
      className={joinClassNames("app-select-dropdown", popupClassName)}
      style={dropdownStyle}
      role="listbox"
    >
      {showSearch && (
        <div className="app-select-search">
          <Search size={14} className="app-select-search__icon" />
          <input
            ref={searchInputRef}
            type="text"
            className="app-select-search__input"
            placeholder="搜索"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            onKeyDown={handleSearchKeyDown}
          />
        </div>
      )}
      <div className="app-select-option-list">
        {filteredOptions.length === 0 ? (
          <div className="app-select-empty">无匹配项</div>
        ) : (
          filteredOptions.map((option, index) => (
            <button
              key={option.value}
              ref={(el) => {
                optionRefs.current[index] = el;
              }}
              type="button"
              className={joinClassNames(
                "app-select-option",
                option.value === value && "app-select-option--selected",
                index === focusedIndex && "app-select-option--focused",
                option.disabled && "app-select-option--disabled"
              )}
              onClick={() => handleOptionSelect(option)}
              role="option"
              aria-selected={option.value === value}
              disabled={option.disabled}
              onMouseEnter={() => setFocusedIndex(index)}
            >
              <span className="app-select-option__label">{option.label}</span>
              {option.value === value && <Check size={14} className="app-select-option__check" />}
            </button>
          ))
        )}
      </div>
    </div>
  ) : null;

  return (
    <div
      ref={containerRef}
      className={joinClassNames(
        "app-select",
        open && "app-select--open",
        disabled && "app-select--disabled",
        className
      )}
      style={style}
    >
      <button
        type="button"
        className="app-select-trigger"
        disabled={disabled}
        onClick={() => {
          if (!disabled) setOpen((prev) => !prev);
        }}
        onKeyDown={handleTriggerKeyDown}
        aria-haspopup="listbox"
        aria-expanded={open}
      >
        <span
          className={joinClassNames(
            "app-select-value",
            !selectedOption && "app-select-value--placeholder"
          )}
        >
          {selectedOption ? selectedOption.label : (placeholder ?? "")}
        </span>
        <span className="app-select-arrow">
          <ChevronDown size={16} className="app-select__suffix-icon" />
        </span>
      </button>

      {typeof document !== "undefined" && createPortal(dropdown, document.body)}
    </div>
  );
}
