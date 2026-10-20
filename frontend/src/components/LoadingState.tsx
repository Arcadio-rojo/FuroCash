export function Spinner() {
  return <span className="loading-spinner" aria-hidden="true" />;
}
export default function LoadingState({
  label,
  rows = 3,
}: {
  label: string;
  rows?: number;
}) {
  return (
    <div className="loading-state" role="status" aria-live="polite">
      <div className="loading-caption">
        <Spinner />
        <span>{label}</span>
      </div>
      <div className="skeleton-stack" aria-hidden="true">
        {Array.from({ length: rows }, (_, i) => (
          <div className="skeleton-row" key={i}>
            <span className="skeleton skeleton-circle" />
            <span className="skeleton-lines">
              <span className="skeleton" />
              <span className="skeleton skeleton-short" />
            </span>
          </div>
        ))}
      </div>
    </div>
  );
}
