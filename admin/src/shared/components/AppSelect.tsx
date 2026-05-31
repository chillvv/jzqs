import type { CSSProperties } from "react";
import { Select } from "antd";
import { ChevronDown } from "lucide-react";

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

function joinClassNames(...values: Array<string | undefined>) {
  return values.filter(Boolean).join(" ");
}

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
  return (
    <Select
      value={value}
      options={options}
      placeholder={placeholder}
      onChange={onChange}
      showSearch={showSearch}
      optionFilterProp="label"
      disabled={disabled}
      style={style}
      suffixIcon={<ChevronDown size={16} className="app-select__suffix-icon" />}
      className={joinClassNames("app-select", className)}
      classNames={{ popup: { root: joinClassNames("app-select-popup", popupClassName) } }}
    />
  );
}
