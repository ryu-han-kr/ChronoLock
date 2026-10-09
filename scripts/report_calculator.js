/* Planning estimates only. Supplier prices checked on 2026-10-10. */
function calculatePlan(v) {
  const keys = ['mau', 'conversion', 'price', 'fixedUsd', 'fx', 'fee', 'tax', 'aiUsers', 'addonPrice', 'calls'];
  for (const key of keys) {
    if (!Number.isFinite(v[key]) || v[key] < 0) throw new Error('모든 값은 0 이상의 숫자로 입력하세요.');
  }
  if (v.fx === 0 || v.fee >= 100 || v.conversion > 100) throw new Error('환율은 0보다 크게, 수수료는 100% 미만, 전환율은 100% 이하로 입력하세요.');
  const paid = Math.floor(v.mau * v.conversion / 100);
  const aiUsers = Math.min(Math.floor(v.aiUsers), paid);
  const gross = paid * v.price + aiUsers * v.addonPrice;
  const netFactor = (1 - v.fee / 100) / (1 + v.tax / 100);
  const net = gross * netFactor;
  const aiUsd = aiUsers * v.calls * ((3000 * .75 + 800 * 4.5) / 1000000) * 1.2;
  const operating = (v.fixedUsd + aiUsd) * v.fx;
  const baseNet = v.price * netFactor;
  return {paid, aiUsers, gross, net, aiUsd, operating, daily: operating / 30,
    remaining: net - operating, breakEven: baseNet > 0 ? Math.ceil(v.fixedUsd * v.fx / baseNet) : null};
}

if (typeof module !== 'undefined') module.exports = {calculatePlan};

if (typeof document !== 'undefined') {
  const money = new Intl.NumberFormat('ko-KR', {maximumFractionDigits: 0});
  const form = document.getElementById('cost-form');
  const update = () => {
    try {
      const values = Object.fromEntries(Array.from(form.querySelectorAll('input')).map(i => [i.name, Number(i.value)]));
      const r = calculatePlan(values);
      const fields = {paid: r.paid + '명', aiUsers: r.aiUsers + '명', gross: money.format(r.gross) + '원',
        net: money.format(r.net) + '원', aiUsd: '$' + r.aiUsd.toFixed(4),
        operating: money.format(r.operating) + '원', daily: money.format(r.daily) + '원',
        remaining: money.format(r.remaining) + '원', breakEven: r.breakEven === null ? '계산 불가' : r.breakEven + '명'};
      for (const [key, value] of Object.entries(fields)) document.getElementById('out-' + key).textContent = value;
      document.getElementById('calc-error').textContent = '';
    } catch (e) { document.getElementById('calc-error').textContent = e.message; }
  };
  form.addEventListener('input', update);
  form.addEventListener('submit', e => e.preventDefault());
  update();
  window.addEventListener('load', () => {
    document.querySelectorAll('.sheet').forEach(s => {
      s.dataset.overflow = String(s.scrollHeight > s.clientHeight + 2);
    });
  });
}
