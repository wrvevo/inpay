import { test } from "node:test";
import assert from "node:assert/strict";
import { toCents, csvCell, splitAmount, assessDemo } from "../src/domain.mjs";
test("money conversion rejects rounding, negatives and invalid separators", () => {
  assert.equal(toCents("10,01"), 1001);
  assert.equal(toCents("0.01"), 1);
  for (const value of ["0", "-1", "10.001", "1.000,20", "NaN", "1e2"])
    assert.throws(() => toCents(value));
});
test("CSV cells neutralize formula injection and quotes", () => {
  assert.equal(csvCell(" =SUM(A1)"), '"\' =SUM(A1)"');
  assert.equal(csvCell('A"B'), '"A""B"');
});
test("split conserves cents across percentages", () => {
  for (let p = 0; p <= 100; p++) {
    const result = splitAmount(1001, p);
    assert.equal(result[0] + result[1], 1001);
  }
  assert.throws(() => splitAmount(100, -1));
  assert.throws(() => splitAmount(100, 20.5));
});
test("fraud boundaries match demo-rules-v1", () => {
  assert.equal(assessDemo(10000, 1, 0, false).decision, "ACCEPT");
  assert.equal(assessDemo(10000, 5, 3, false).decision, "REVIEW");
  assert.equal(assessDemo(10000, 1, 0, true).decision, "REJECT");
  assert.equal(assessDemo(100001, 5, 3, true).score, 100);
});
