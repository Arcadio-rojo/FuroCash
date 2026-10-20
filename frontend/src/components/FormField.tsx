import { useState } from "react";
import type { InputHTMLAttributes } from "react";
type Props = Omit<InputHTMLAttributes<HTMLInputElement>, "onChange"> & {
  label: string;
  onChange: (value: string) => void;
  hint?: string;
};
export default function FormField({
  label,
  id,
  hint,
  onChange,
  type,
  ...props
}: Props) {
  const [visible, setVisible] = useState(false);
  return (
    <div className="field">
      <label htmlFor={id}>{label}</label>
      <div className="input-wrap">
        <input
          {...props}
          id={id}
          type={type === "password" && visible ? "text" : type}
          onChange={(e) => onChange(e.target.value)}
          aria-describedby={hint ? `${id}-hint` : undefined}
        />
        {type === "password" && (
          <button
            className="reveal"
            type="button"
            aria-label={visible ? `Hide ${label}` : `Show ${label}`}
            onClick={() => setVisible(!visible)}
          >
            {visible ? "Hide" : "Show"}
          </button>
        )}
      </div>
      {hint && <small id={`${id}-hint`}>{hint}</small>}
    </div>
  );
}
