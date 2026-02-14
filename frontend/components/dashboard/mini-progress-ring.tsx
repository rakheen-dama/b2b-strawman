interface MiniProgressRingProps {
  value: number;
  size?: number;
  color?: string;
}

function autoColor(value: number): string {
  if (value > 66) return "var(--color-green-500, #22c55e)";
  if (value > 33) return "var(--color-amber-500, #f59e0b)";
  return "var(--color-red-500, #ef4444)";
}

export function MiniProgressRing({
  value,
  size = 32,
  color,
}: MiniProgressRingProps) {
  const clampedValue = Number.isFinite(value) ? Math.max(0, Math.min(100, value)) : 0;
  const resolvedColor = color ?? autoColor(clampedValue);

  const strokeWidth = size >= 40 ? 4 : 3;
  const radius = (size - strokeWidth) / 2;
  const circumference = 2 * Math.PI * radius;
  const offset = circumference - (clampedValue / 100) * circumference;

  const center = size / 2;
  const showText = size >= 40;

  return (
    <svg
      width={size}
      height={size}
      viewBox={`0 0 ${size} ${size}`}
      aria-label={`${Math.round(clampedValue)}%`}
      role="img"
      className="inline-block"
    >
      {/* Background circle */}
      <circle
        cx={center}
        cy={center}
        r={radius}
        fill="none"
        stroke="var(--color-muted, #e5e5e5)"
        strokeWidth={strokeWidth}
      />
      {/* Foreground arc */}
      <circle
        cx={center}
        cy={center}
        r={radius}
        fill="none"
        stroke={resolvedColor}
        strokeWidth={strokeWidth}
        strokeDasharray={circumference}
        strokeDashoffset={offset}
        strokeLinecap="round"
        transform={`rotate(-90 ${center} ${center})`}
      />
      {/* Center text */}
      {showText && (
        <text
          x={center}
          y={center}
          textAnchor="middle"
          dominantBaseline="central"
          fontSize={size * 0.28}
          fill="currentColor"
          fontWeight={600}
        >
          {Math.round(clampedValue)}%
        </text>
      )}
    </svg>
  );
}
