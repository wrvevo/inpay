/** Parse a decimal BRL value without floating-point multiplication or silent rounding. */
export function toCents(value) {
  if (!/^\d{1,9}([.,]\d{1,2})?$/.test(String(value)))
    throw new Error("Informe um valor com até duas casas decimais.");
  const [whole, fraction = ""] = String(value).replace(",", ".").split(".");
  const cents = Number(whole) * 100 + Number(fraction.padEnd(2, "0"));
  if (cents <= 0) throw new Error("O valor deve ser maior que zero.");
  return cents;
}

/** Prevent spreadsheet formula execution, including formulas preceded by whitespace. */
export function csvCell(value) {
  let text = String(value ?? "");
  if (/^[\s]*[=+@-]/.test(text)) text = "'" + text;
  return '"' + text.replaceAll('"', '""') + '"';
}

/** Integer allocation preserves every cent; the final receiver absorbs the remainder. */
export function splitAmount(amount, percentage) {
  if (
    !Number.isSafeInteger(amount) ||
    amount < 1 ||
    !Number.isInteger(percentage) ||
    percentage < 0 ||
    percentage > 100
  )
    throw new Error("Divisão inválida.");
  const fee = Math.floor((amount * percentage) / 100);
  return [fee, amount - fee];
}

/** Mirrors demo-rules-v1 for local simulations only; connected decisions come from Java. */
export function assessDemo(amount, attempts, declines, compromised) {
  const score = Math.min(
    100,
    (compromised ? 70 : 0) +
      (attempts >= 5 ? 30 : 0) +
      (declines >= 3 ? 25 : 0) +
      (amount > 100000 ? 20 : 0),
  );
  return {
    score,
    decision: score >= 70 ? "REJECT" : score >= 40 ? "REVIEW" : "ACCEPT",
    policyVersion: "demo-rules-v1",
  };
}
