import React from "react";
import ReactDatePicker from "react-datepicker";
import "react-datepicker/dist/react-datepicker.css";
import "./DatePicker.css";

interface DatePickerProps {
  value: string;
  onChange: (date: string) => void;
  showTomorrowShortcut?: boolean;
}

export function DatePicker({ value, onChange, showTomorrowShortcut = true }: DatePickerProps) {
  // 解析日期字符串，避免时区问题
  const selectedDate = React.useMemo(() => {
    if (!value) return new Date();
    const [year, month, day] = value.split('-').map(Number);
    return new Date(year, month - 1, day);
  }, [value]);

  const handleChange = (date: Date | null) => {
    if (date) {
      const year = date.getFullYear();
      const month = String(date.getMonth() + 1).padStart(2, "0");
      const day = String(date.getDate()).padStart(2, "0");
      onChange(`${year}-${month}-${day}`);
    }
  };

  const handleTomorrowClick = () => {
    const tomorrow = new Date();
    tomorrow.setDate(tomorrow.getDate() + 1);
    const year = tomorrow.getFullYear();
    const month = String(tomorrow.getMonth() + 1).padStart(2, "0");
    const day = String(tomorrow.getDate()).padStart(2, "0");
    onChange(`${year}-${month}-${day}`);
  };

  return (
    <div className="date-picker-wrapper">
      <ReactDatePicker
        selected={selectedDate}
        onChange={handleChange}
        dateFormat="yyyy-MM-dd"
        className="input-box date-picker-input"
      />
      {showTomorrowShortcut && (
        <button
          type="button"
          className="btn btn-outline btn-compact date-picker-shortcut"
          onClick={handleTomorrowClick}
        >
          明天
        </button>
      )}
    </div>
  );
}
