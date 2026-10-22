let products = [];
const money = value => `$${Number(value).toFixed(2)}`;
const escapeHtml = value => String(value).replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#039;'}[c]));

async function loadDashboard() {
  const response = await fetch('/api/inventory');
  const data = await response.json();
  document.querySelector('#products').textContent = data.products;
  document.querySelector('#units').textContent = data.units;
  document.querySelector('#value').textContent = money(data.inventoryValue);
  document.querySelector('#profit').textContent = money(data.profit);
  document.querySelector('#lowStock').textContent = `${data.lowStock} low-stock items`;
}

async function loadProducts() {
  const response = await fetch('/api/inventory?view=products');
  products = await response.json();
  renderProducts(products);
  const select = document.querySelector('#product');
  select.innerHTML = products.map(product => `<option value="${product.id}">${escapeHtml(product.name)} (${product.stock} in stock)</option>`).join('');
}

function renderProducts(items) {
  document.querySelector('#productsGrid').innerHTML = items.map(product => `
    <article class="product" data-id="${product.id}">
      <div class="product-top"><div><h3>${escapeHtml(product.name)}</h3><p>${escapeHtml(product.category)} · ${product.id}</p></div>
      <span class="stock ${product.stock < 25 ? 'low' : ''}">${product.stock} units</span></div>
      <div class="product-bottom"><b>${money(product.sellingPrice)}</b><span>margin ${money(product.margin)}</span></div>
    </article>`).join('');
}

document.querySelector('#search').addEventListener('input', event => {
  const term = event.target.value.toLowerCase();
  renderProducts(products.filter(product => `${product.name} ${product.category} ${product.id}`.toLowerCase().includes(term)));
});

document.querySelector('#actionForm').addEventListener('submit', async event => {
  event.preventDefault();
  const body = new URLSearchParams({
    action: document.querySelector('#action').value,
    productId: document.querySelector('#product').value,
    quantity: document.querySelector('#quantity').value,
    customer: document.querySelector('#customer').value,
  });
  const response = await fetch('/api/inventory', { method: 'POST', headers: {'Content-Type': 'application/x-www-form-urlencoded'}, body });
  const result = await response.json();
  const notice = document.querySelector('#notice');
  notice.textContent = result.error || `${result.message}: ${result.quantity} × ${result.product} (${money(result.total)})`;
  notice.style.background = result.error ? '#fff0ed' : '#e8faf3';
  notice.style.color = result.error ? '#b84f3c' : '#168062';
  if (!result.error) { await loadProducts(); await loadDashboard(); }
});

document.querySelector('#reportButton').addEventListener('click', async () => {
  const params = new URLSearchParams({view:'report', from:document.querySelector('#from').value, to:document.querySelector('#to').value});
  const response = await fetch(`/api/inventory?${params}`);
  const report = await response.json();
  document.querySelector('#report').innerHTML = report.length ? report.map(item => `<div class="report-item"><strong>${escapeHtml(item.type)} · ${escapeHtml(item.product)}</strong><span>${item.date} · ${item.quantity} units · ${money(item.total)}</span></div>`).join('') : '<p class="notice">No transactions in this range.</p>';
});

loadProducts();
loadDashboard();
