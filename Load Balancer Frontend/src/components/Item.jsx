export default function Item({ label, value }) {
  return (
    <div
      className="
            rounded-2xl
            bg-white/5
            p-4
        "
    >
      <p className="text-slate-400">{label}</p>

      <p className="text-xl font-bold">{value}</p>
    </div>
  );
}
