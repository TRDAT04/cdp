// ============================================================
// CDP VNPost - Main Application Logic
// ============================================================

'use strict';

// ---- State ----
let currentPage = 'dashboard';
let selectedCustomer = null;
let currentSegmentId = null;
let currentCustomerPage = 1;
let filteredCustomers = [];
const PAGE_SIZE = 15;

// ---- DOM Ready ----
document.addEventListener('DOMContentLoaded', () => {
  initApp();
});

function initApp() {
  buildSidebar();
  buildCharts();
  renderDashboard();
  renderCustomerList();
  renderIdentityResolution();
  renderSegments();
  renderTimeline();
  setupSearch();
  initRules();
  initEvents();
  navigateTo('dashboard');
}

// ============================================================
// NAVIGATION
// ============================================================

function buildSidebar() {
  const navItems = [
    { id: 'dashboard', icon: 'fa-chart-pie', label: 'Dashboard', section: 'TỔNG QUAN' },
    { id: 'customers', icon: 'fa-users', label: 'Quản lý Khách hàng', section: null },
    { id: 'c360', icon: 'fa-id-card', label: 'Customer 360', section: null },
    { id: 'identity', icon: 'fa-link', label: 'Identity Resolution', badge: CDP_DATA.duplicateProfiles.filter(d => d.status === 'pending').length, section: 'NGHIỆP VỤ' },
    { id: 'timeline', icon: 'fa-stream', label: 'Customer Timeline', section: null },
    { id: 'segments', icon: 'fa-layer-group', label: 'Segment Management', section: null },
    { id: 'rules', icon: 'fa-shield-alt', label: 'Rule Management', section: 'CẤU HÌNH' },
    { id: 'events', icon: 'fa-bolt', label: 'Event Management', section: null },
  ];

  const nav = document.getElementById('sidebarNav');
  let lastSection = null;

  navItems.forEach(item => {
    if (item.section && item.section !== lastSection) {
      const label = document.createElement('div');
      label.className = 'nav-section-label';
      label.textContent = item.section;
      nav.appendChild(label);
      lastSection = item.section;
    }

    const el = document.createElement('div');
    el.className = 'nav-item';
    el.id = `nav-${item.id}`;
    el.setAttribute('data-page', item.id);
    el.innerHTML = `
      <span class="nav-icon"><i class="fas ${item.icon}"></i></span>
      <span>${item.label}</span>
      ${item.badge ? `<span class="nav-badge">${item.badge}</span>` : ''}
    `;
    el.addEventListener('click', () => navigateTo(item.id));
    nav.appendChild(el);
  });
}

function navigateTo(page, data = null) {
  currentPage = page;

  // Update nav items
  document.querySelectorAll('.nav-item').forEach(el => el.classList.remove('active'));
  const navEl = document.getElementById(`nav-${page}`);
  if (navEl) navEl.classList.add('active');

  // Update page sections
  document.querySelectorAll('.page-section').forEach(el => el.classList.remove('active'));
  const pageEl = document.getElementById(`page-${page}`);
  if (pageEl) pageEl.classList.add('active');

  // Update header
  updateHeader(page, data);

  // Page-specific actions
  if (page === 'c360') {
    // If no customer passed (e.g. clicked from sidebar), use last selected or first customer
    const customerToShow = data || selectedCustomer || CDP_DATA.customers[0];
    selectedCustomer = customerToShow;
    renderCustomer360(customerToShow);
    // Update header subtitle with loaded customer
    document.getElementById('headerSub').textContent = `Hồ sơ: ${customerToShow.cdpId} – ${customerToShow.name}`;
  }

  if (page === 'segment-members' && data) {
    currentSegmentId = data;
    renderSegmentMembers(data);
  }

  window.scrollTo(0, 0);
}

function updateHeader(page, data) {
  const titles = {
    dashboard: { title: 'Dashboard', sub: 'Tổng quan hệ thống VNPost CDP' },
    customers: { title: 'Quản lý Khách hàng', sub: `${CDP_DATA.customers.length} khách hàng trong hệ thống` },
    c360: { title: 'Customer 360', sub: data ? `Hồ sơ: ${data.cdpId}` : 'Xem chi tiết khách hàng' },
    identity: { title: 'Identity Resolution', sub: 'Phát hiện và hợp nhất hồ sơ trùng lặp' },
    timeline: { title: 'Customer Timeline', sub: 'Lịch sử hoạt động khách hàng' },
    segments: { title: 'Segment Management', sub: `${CDP_DATA.segments.length} phân khúc khách hàng` },
    'segment-members': { title: 'Thành viên Segment', sub: '' },
    rules: { title: 'Rule Management', sub: 'Quản lý các quy tắc xử lý dữ liệu CDP' },
    events: { title: 'Event Management', sub: 'Toàn bộ sự kiện thu thập từ các nguồn' },
  };

  const info = titles[page] || titles['dashboard'];
  document.getElementById('headerTitle').textContent = info.title;
  document.getElementById('headerSub').textContent = info.sub;
}

// ============================================================
// DASHBOARD
// ============================================================

function renderDashboard() {
  // Stats
  const stats = CDP_DATA.stats;

  document.getElementById('statTotalCustomers').textContent = stats.totalCustomers.toLocaleString('vi-VN');
  document.getElementById('statMerged').textContent = stats.totalMerged;
  document.getElementById('statEvents').textContent = stats.totalEvents.toLocaleString('vi-VN');
  document.getElementById('statSegments').textContent = stats.totalSegments;

  // Suspect count = pairs still pending * 2 (each pair = 2 customers)
  const suspectCount = CDP_DATA.duplicateProfiles.filter(d => d.status === 'pending').length * 2;
  const suspectEl = document.getElementById('statSuspect');
  if (suspectEl) suspectEl.textContent = suspectCount;

  // Province bar chart
  renderProvinceChart();
}

function renderProvinceChart() {
  const stats = CDP_DATA.provinceStats;
  const sorted = Object.entries(stats).sort((a, b) => b[1] - a[1]).slice(0, 12);
  const max = sorted[0][1];

  const container = document.getElementById('provinceList');
  container.innerHTML = sorted.map(([province, count]) => `
    <div class="province-item">
      <div class="province-name" title="${province}">${province}</div>
      <div class="province-bar-wrap">
        <div class="province-bar" style="width: ${(count / max * 100).toFixed(1)}%"></div>
      </div>
      <div class="province-count">${count}</div>
    </div>
  `).join('');
}

// ============================================================
// CHARTS (Chart.js)
// ============================================================

function buildCharts() {
  buildCustomerTypeChart();
  buildEventTrendChart();
}

function buildCustomerTypeChart() {
  const ctx = document.getElementById('chartCustomerType');
  if (!ctx) return;

  const vipCount = CDP_DATA.customers.filter(c => c.customerType === 'VIP').length;
  const regularCount = CDP_DATA.customers.filter(c => c.customerType === 'Thường xuyên').length;
  const individualCount = CDP_DATA.customers.filter(c => c.customerType === 'Cá nhân').length;
  const businessCount = CDP_DATA.businessCustomers.length;

  new Chart(ctx, {
    type: 'doughnut',
    data: {
      labels: ['VIP', 'Thường xuyên', 'Cá nhân', 'Doanh nghiệp'],
      datasets: [{
        data: [vipCount, regularCount, individualCount, businessCount],
        backgroundColor: ['#f59e0b', '#8b5cf6', '#3b82f6', '#1a3a6b'],
        borderWidth: 0,
        hoverOffset: 6,
      }]
    },
    options: {
      cutout: '65%',
      responsive: true,
      maintainAspectRatio: false,
      plugins: {
        legend: {
          position: 'bottom',
          labels: {
            font: { family: 'Inter', size: 11 },
            padding: 12,
            usePointStyle: true,
            pointStyleWidth: 8,
          }
        },
        tooltip: {
          callbacks: {
            label: (ctx) => ` ${ctx.label}: ${ctx.raw} KH`
          }
        }
      }
    }
  });
}

function buildEventTrendChart() {
  const ctx = document.getElementById('chartEventTrend');
  if (!ctx) return;

  // Group events by last 14 days
  const days = [];
  const counts = [];
  for (let i = 13; i >= 0; i--) {
    const d = new Date();
    d.setDate(d.getDate() - i);
    const dateStr = d.toISOString().split('T')[0];
    days.push(d.toLocaleDateString('vi-VN', { day: '2-digit', month: '2-digit' }));
    counts.push(CDP_DATA.events.filter(e => e.date === dateStr).length);
  }

  new Chart(ctx, {
    type: 'bar',
    data: {
      labels: days,
      datasets: [{
        label: 'Sự kiện',
        data: counts,
        backgroundColor: 'rgba(37,80,160,.15)',
        borderColor: '#2550a0',
        borderWidth: 2,
        borderRadius: 6,
        borderSkipped: false,
        hoverBackgroundColor: 'rgba(245,166,35,.3)',
        hoverBorderColor: '#f5a623',
      }]
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      plugins: {
        legend: { display: false },
        tooltip: {
          callbacks: { label: (ctx) => ` ${ctx.raw} sự kiện` }
        }
      },
      scales: {
        x: {
          grid: { display: false },
          ticks: { font: { family: 'Inter', size: 10 }, color: '#94a3b8' },
        },
        y: {
          grid: { color: 'rgba(0,0,0,.04)', drawBorder: false },
          ticks: { font: { family: 'Inter', size: 10 }, color: '#94a3b8', stepSize: 1 },
          beginAtZero: true,
        }
      }
    }
  });
}

// ============================================================
// CUSTOMER MANAGEMENT
// ============================================================

function renderCustomerList(customers = CDP_DATA.customers, page = 1) {
  filteredCustomers = customers;
  currentCustomerPage = page;

  const start = (page - 1) * PAGE_SIZE;
  const end = start + PAGE_SIZE;
  const pageData = customers.slice(start, end);

  const tbody = document.getElementById('customerTableBody');
  if (!tbody) return;

  tbody.innerHTML = pageData.map(c => {
    const initials = c.name.split(' ').map(w => w[0]).join('').substring(0, 2).toUpperCase();
    const bgColor = stringToColor(c.cdpId);
    const typeBadge = getTypeBadge(c.customerType, c.type);
    const dupStatus = c.duplicateStatus || 'normal';
    const alertBadge = getDupAlertBadge(dupStatus);
    const hasMergeBtn = dupStatus === 'suspect';
    return `
      <tr onclick="openCustomer360('${c.id}')" style="cursor:pointer">
        <td>
          <div class="customer-cell">
            <div class="avatar-initials" style="background: ${bgColor}">${initials}</div>
            <div>
              <div class="customer-name">${c.name}</div>
              <div class="customer-id">${c.cdpId}</div>
            </div>
          </div>
        </td>
        <td><span class="font-mono" style="font-size:12px">${c.cdpId}</span></td>
        <td>${c.phone}</td>
        <td><a href="mailto:${c.email}" onclick="event.stopPropagation()" style="color:var(--primary)">${c.email}</a></td>
        <td>${typeBadge}</td>
        <td>${alertBadge}</td>
        <td>
          ${c.sources.map(s => `<span class="badge-type badge-info" style="margin-right:3px;font-size:10px">${s}</span>`).join('')}
        </td>
        <td>
          <span class="badge-type badge-${getActivityBadge(c.lastActivity)}">${c.lastActivity}</span>
        </td>
        <td>
          <div style="display:flex;gap:6px;align-items:center">
            <button class="btn btn-sm btn-outline" onclick="event.stopPropagation(); openCustomer360('${c.id}')">
              <i class="fas fa-eye"></i> Xem
            </button>
            ${hasMergeBtn ? `
            <button class="btn btn-sm btn-merge-alert" onclick="event.stopPropagation(); openCustomerDupModal('${c.id}')" id="merge-btn-${c.id}">
              <i class="fas fa-compress-arrows-alt"></i> Xem & Merge
            </button>` : ''}
          </div>
        </td>
      </tr>
    `;
  }).join('') || '<tr><td colspan="9" class="text-center py-4 text-muted">Không tìm thấy kết quả</td></tr>';

  // Update count
  const countEl = document.getElementById('customerCount');
  if (countEl) countEl.textContent = `${customers.length} khách hàng`;

  // Pagination
  renderPagination(customers.length, page);
}

function renderPagination(total, currentPage) {
  const totalPages = Math.ceil(total / PAGE_SIZE);
  const container = document.getElementById('customerPagination');
  if (!container) return;

  const paginationInfo = document.getElementById('paginationInfo');
  const start = (currentPage - 1) * PAGE_SIZE + 1;
  const end = Math.min(currentPage * PAGE_SIZE, total);
  if (paginationInfo) paginationInfo.textContent = `Hiển thị ${start}–${end} / ${total}`;

  let buttons = '';
  const prev = `<button class="page-btn" onclick="changePage(${currentPage - 1})" ${currentPage === 1 ? 'disabled' : ''}><i class="fas fa-chevron-left"></i></button>`;
  const next = `<button class="page-btn" onclick="changePage(${currentPage + 1})" ${currentPage === totalPages ? 'disabled' : ''}><i class="fas fa-chevron-right"></i></button>`;

  let pages = '';
  for (let i = 1; i <= totalPages; i++) {
    if (i === 1 || i === totalPages || (i >= currentPage - 1 && i <= currentPage + 1)) {
      pages += `<button class="page-btn ${i === currentPage ? 'active' : ''}" onclick="changePage(${i})">${i}</button>`;
    } else if (i === currentPage - 2 || i === currentPage + 2) {
      pages += `<span style="padding:0 4px;color:var(--text-muted)">…</span>`;
    }
  }

  container.innerHTML = prev + pages + next;
}

function changePage(page) {
  const total = filteredCustomers.length;
  const totalPages = Math.ceil(total / PAGE_SIZE);
  if (page < 1 || page > totalPages) return;
  renderCustomerList(filteredCustomers, page);
}

function setupSearch() {
  const inputs = ['searchName', 'searchPhone', 'searchEmail', 'searchCdpId'];
  inputs.forEach(id => {
    const el = document.getElementById(id);
    if (el) {
      el.addEventListener('input', debounce(filterCustomers, 300));
    }
  });
}

function filterCustomers() {
  const name = (document.getElementById('searchName')?.value || '').toLowerCase();
  const phone = (document.getElementById('searchPhone')?.value || '').toLowerCase();
  const email = (document.getElementById('searchEmail')?.value || '').toLowerCase();
  const cdpId = (document.getElementById('searchCdpId')?.value || '').toLowerCase();

  const filtered = CDP_DATA.customers.filter(c => {
    return (!name || c.name.toLowerCase().includes(name))
      && (!phone || c.phone.includes(phone))
      && (!email || c.email.toLowerCase().includes(email))
      && (!cdpId || c.cdpId.toLowerCase().includes(cdpId));
  });

  renderCustomerList(filtered, 1);
}

function openCustomer360(customerId) {
  const customer = CDP_DATA.customers.find(c => c.id === customerId);
  if (!customer) return;
  // navigateTo handles section switching + renderCustomer360
  navigateTo('c360', customer);
}

// ============================================================
// CUSTOMER 360
// ============================================================

function renderCustomer360(customer) {
  const c = customer;
  const initials = c.name.split(' ').map(w => w[0]).join('').substring(0, 2).toUpperCase();
  const bgColor = stringToColor(c.cdpId);

  // Golden Record
  document.getElementById('c360Avatar').innerHTML = `<div class="avatar-lg-initials" style="background: ${bgColor}">${initials}</div>`;
  document.getElementById('c360Name').textContent = c.name;
  document.getElementById('c360CdpId').textContent = c.cdpId;

  document.getElementById('c360Details').innerHTML = `
    <div class="info-row">
      <div class="info-row-icon"><i class="fas fa-phone"></i></div>
      <div>
        <div class="info-row-label">Số điện thoại</div>
        <div class="info-row-value">${c.phone}</div>
      </div>
    </div>
    <div class="info-row">
      <div class="info-row-icon"><i class="fas fa-envelope"></i></div>
      <div>
        <div class="info-row-label">Email</div>
        <div class="info-row-value">${c.email}</div>
      </div>
    </div>
    <div class="info-row">
      <div class="info-row-icon"><i class="fas fa-map-marker-alt"></i></div>
      <div>
        <div class="info-row-label">Địa chỉ</div>
        <div class="info-row-value">${c.address}</div>
      </div>
    </div>
    <div class="info-row">
      <div class="info-row-icon"><i class="fas fa-tag"></i></div>
      <div>
        <div class="info-row-label">Loại khách hàng</div>
        <div class="info-row-value">${getTypeBadge(c.customerType, c.type)}</div>
      </div>
    </div>
    <div class="info-row">
      <div class="info-row-icon"><i class="fas fa-calendar"></i></div>
      <div>
        <div class="info-row-label">Ngày tạo</div>
        <div class="info-row-value">${c.createdAt}</div>
      </div>
    </div>
    ${c.type === 'business' ? `
    <div class="info-row">
      <div class="info-row-icon"><i class="fas fa-file-invoice"></i></div>
      <div>
        <div class="info-row-label">Mã số thuế</div>
        <div class="info-row-value font-mono">${c.taxCode}</div>
      </div>
    </div>` : ''}
    <div class="info-row">
      <div class="info-row-icon"><i class="fas fa-shopping-cart"></i></div>
      <div>
        <div class="info-row-label">Tổng đơn hàng</div>
        <div class="info-row-value">${c.totalOrders} đơn</div>
      </div>
    </div>
    <div class="info-row">
      <div class="info-row-icon"><i class="fas fa-coins"></i></div>
      <div>
        <div class="info-row-label">Tổng chi tiêu</div>
        <div class="info-row-value text-accent fw-700">${c.totalSpent.toLocaleString('vi-VN')} ₫</div>
      </div>
    </div>
  `;

  // Identity Mapping
  const mappingEl = document.getElementById('c360Mapping');
  const sources = ['CRM', 'CMS', 'Portal', 'MyVNPost'];
  const colors = { CRM: '#1a3a6b', CMS: '#2550a0', Portal: '#8b5cf6', MyVNPost: '#10b981' };

  mappingEl.innerHTML = sources.map(src => {
    const mappedId = c.identityMapping[src];
    return `
      <div class="mapping-item">
        <span class="mapping-source" style="background:${colors[src]}">${src}</span>
        <i class="fas fa-arrow-right text-muted" style="font-size:11px"></i>
        ${mappedId
          ? `<span class="mapping-id">${mappedId}</span>`
          : `<span class="mapping-null">Không có</span>`
        }
      </div>
    `;
  }).join('');

  // Segments
  const segEl = document.getElementById('c360Segments');
  if (c.segments.length === 0) {
    segEl.innerHTML = '<span class="text-muted" style="font-size:12px">Chưa thuộc segment nào</span>';
  } else {
    segEl.innerHTML = c.segments.map(segId => {
      const seg = CDP_DATA.segments.find(s => s.id === segId);
      if (!seg) return '';
      return `
        <span class="segment-tag" style="background: ${seg.color}22; color: ${seg.color}; border: 1px solid ${seg.color}44">
          <i class="fas ${seg.icon}"></i> ${seg.name}
        </span>
      `;
    }).join('');
  }

  // KPI summary
  document.getElementById('c360Kpi').innerHTML = `
    <div class="kpi-mini">
      <i class="fas fa-box" style="color:var(--primary);font-size:18px"></i>
      <div>
        <div class="kpi-mini-val">${c.totalOrders}</div>
        <div class="kpi-mini-label">Tổng đơn</div>
      </div>
    </div>
    <div class="kpi-mini">
      <i class="fas fa-coins" style="color:var(--accent);font-size:18px"></i>
      <div>
        <div class="kpi-mini-val">${(c.totalSpent / 1000000).toFixed(1)}M</div>
        <div class="kpi-mini-label">Chi tiêu (VNĐ)</div>
      </div>
    </div>
    <div class="kpi-mini">
      <i class="fas fa-calendar-check" style="color:var(--success);font-size:18px"></i>
      <div>
        <div class="kpi-mini-val">${c.lastActivity}</div>
        <div class="kpi-mini-label">Hoạt động cuối</div>
      </div>
    </div>
  `;

  // Recent Activities (timeline)
  const customerEvents = CDP_DATA.events
    .filter(e => e.customerId === c.id)
    .slice(0, 12);

  const timelineEl = document.getElementById('c360Timeline');
  if (customerEvents.length === 0) {
    const syntheticEvents = generateSyntheticEvents(c);
    timelineEl.innerHTML = renderTimelineItems(syntheticEvents);
  } else {
    timelineEl.innerHTML = renderTimelineItems(customerEvents);
  }

  // Attribute Change History
  renderAttributeHistory(c);

  // Reset to Activities tab whenever a new customer is loaded
  switchC360Tab('activities');
}

// ============================================================
// ATTRIBUTE CHANGE HISTORY
// ============================================================

function renderAttributeHistory(customer) {
  const history = customer.attributeHistory || [];

  // Update tab count badge
  const countEl = document.getElementById('attrHistoryCount');
  if (countEl) countEl.textContent = history.length;

  const container = document.getElementById('c360AttrHistory');
  if (!container) return;

  if (history.length === 0) {
    container.innerHTML = `
      <div class="history-empty">
        <i class="fas fa-check-circle" style="color:var(--success)"></i>
        <p>Không có thay đổi thuộc tính nào được ghi nhận</p>
      </div>`;
    return;
  }

  // Source chip class helper
  const sourceClass = (src) => ({
    CRM: 'source-chip-crm',
    CMS: 'source-chip-cms',
    Portal: 'source-chip-portal',
    MyVNPost: 'source-chip-myvnpost',
  })[src] || 'source-chip-crm';

  // Attribute badge class helper
  const attrClass = (key) => ({
    email: 'attr-badge-email',
    phone: 'attr-badge-phone',
    address: 'attr-badge-address',
    name: 'attr-badge-name',
  })[key] || 'attr-badge-email';

  const rows = history.map(h => {
    const d = new Date(h.date);
    const dateStr = d.toLocaleDateString('vi-VN', { day: '2-digit', month: '2-digit', year: 'numeric' });
    return `
      <tr>
        <td style="color:var(--text-muted);white-space:nowrap;font-size:11px">${dateStr}</td>
        <td>
          <span class="attr-badge ${attrClass(h.attribute)}">
            <i class="fas ${h.icon}"></i>
            ${h.attributeLabel}
          </span>
        </td>
        <td>
          <div class="diff-cell">
            <span class="diff-old" title="${h.oldValue}">${h.oldValue}</span>
          </div>
        </td>
        <td style="text-align:center">
          <span class="diff-arrow">↗</span>
        </td>
        <td>
          <span class="diff-new" title="${h.newValue}">${h.newValue}</span>
        </td>
        <td>
          <span class="source-chip ${sourceClass(h.source)}">${h.source}</span>
        </td>
        <td style="font-size:11px;color:var(--text-muted)">${h.changedBy}</td>
      </tr>
    `;
  }).join('');

  container.innerHTML = `
    <div class="history-banner">
      <div>
        <div class="history-banner-count">${history.length}</div>
        <div class="history-banner-label">thay đổi<br>được ghi nhận</div>
      </div>
      <div style="flex:1">
        <div style="font-size:13px;font-weight:700;color:var(--text-primary);margin-bottom:2px">
          📋 Lịch sử thuộc tính – ${customer.name}
        </div>
        <div style="font-size:11px;color:var(--text-muted)">
          Dữ liệu từ ${[...new Set(history.map(h => h.source))].join(', ')}
        </div>
      </div>
    </div>
    <div style="overflow-x:auto">
      <table class="attr-history-table">
        <thead>
          <tr>
            <th>Ngày</th>
            <th>Thuộc tính</th>
            <th>Giá trị cũ</th>
            <th></th>
            <th>Giá trị mới</th>
            <th>Nguồn</th>
            <th>Hệ thống</th>
          </tr>
        </thead>
        <tbody>${rows}</tbody>
      </table>
    </div>
  `;
}

function switchC360Tab(tab) {
  // Update tab buttons
  document.querySelectorAll('.card-tab-btn').forEach(btn => {
    btn.classList.toggle('active', btn.getAttribute('data-tab') === tab);
  });
  // Update panels
  document.querySelectorAll('.tab-panel').forEach(panel => {
    panel.classList.remove('active');
  });
  const activePanel = document.getElementById(
    tab === 'activities' ? 'panel-activities' : 'panel-attr-history'
  );
  if (activePanel) activePanel.classList.add('active');
}

function generateSyntheticEvents(customer) {
  const eventDefs = [
    { type: 'register', label: 'Đăng ký tài khoản', icon: 'fa-user-plus', color: '#10b981' },
    { type: 'login', label: 'Đăng nhập hệ thống', icon: 'fa-sign-in-alt', color: '#3b82f6' },
    { type: 'view_product', label: 'Xem danh sách dịch vụ', icon: 'fa-eye', color: '#6b7280' },
    { type: 'create_order', label: 'Tạo đơn hàng mới', icon: 'fa-box', color: '#10b981' },
    { type: 'payment', label: 'Thanh toán đơn hàng', icon: 'fa-credit-card', color: '#8b5cf6' },
    { type: 'track_shipment', label: 'Theo dõi bưu phẩm', icon: 'fa-truck', color: '#f59e0b' },
    { type: 'rate_service', label: 'Đánh giá dịch vụ', icon: 'fa-star', color: '#f59e0b' },
    { type: 'update_profile', label: 'Cập nhật thông tin', icon: 'fa-user-edit', color: '#06b6d4' },
  ];

  return eventDefs.map((e, idx) => {
    const d = new Date();
    d.setDate(d.getDate() - (idx * 7 + Math.floor(Math.random() * 5)));
    return {
      ...e,
      id: `SYNTH${idx}`,
      timestamp: d.toISOString(),
      date: d.toISOString().split('T')[0],
      source: customer.sources[idx % customer.sources.length] || 'CRM',
    };
  });
}

function renderTimelineItems(events) {
  if (events.length === 0) return '<div class="empty-state"><i class="fas fa-history"></i><p>Không có hoạt động</p></div>';

  return events.map(e => {
    const dt = new Date(e.timestamp);
    const timeStr = dt.toLocaleString('vi-VN', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' });
    const detail = formatEventDetail(e);
    return `
      <div class="timeline-item">
        <div class="timeline-dot" style="background: ${e.color}">
          <i class="fas ${e.icon}" style="font-size:8px"></i>
        </div>
        <div class="timeline-time">${timeStr} · <span style="color:var(--primary);font-size:10px">${e.source}</span></div>
        <div class="timeline-content">
          <div class="timeline-label">${e.label}</div>
          ${detail ? `<div class="timeline-detail">${detail}</div>` : ''}
        </div>
      </div>
    `;
  }).join('');
}

function formatEventDetail(event) {
  const d = event.details || {};
  switch (event.type) {
    case 'create_order': return d.orderId ? `Mã đơn: ${d.orderId} · ${d.service} · ${d.amount?.toLocaleString('vi-VN')} ₫` : '';
    case 'payment': return d.amount ? `${d.amount.toLocaleString('vi-VN')} ₫ · ${d.method}` : '';
    case 'track_shipment': return d.trackingCode ? `${d.trackingCode} · ${d.status}` : '';
    case 'rate_service': return d.rating ? `${'⭐'.repeat(d.rating)} · "${d.comment}"` : '';
    case 'login': return d.device ? `${d.device} · IP: ${d.ip}` : '';
    default: return '';
  }
}

// ============================================================
// IDENTITY RESOLUTION
// ============================================================

function renderIdentityResolution() {
  const container = document.getElementById('dupContainer');
  if (!container) return;

  container.innerHTML = CDP_DATA.duplicateProfiles.map(dup => renderDupCard(dup)).join('');
}

function renderDupCard(dup) {
  const scoreClass = dup.matchScore >= 95 ? 'score-high' : 'score-medium';
  const isMerged = dup.status === 'merged';

  return `
    <div class="dup-card ${isMerged ? 'merged' : ''}" id="dup-${dup.id}">
      <div class="dup-header">
        <div class="dup-score">
          <div class="score-circle ${scoreClass}">
            ${dup.matchScore}%
          </div>
          <div>
            <div class="score-value fw-700">Match Score</div>
            <div class="score-label">${dup.matchScore >= 95 ? '🔴 Rất cao' : '🟡 Cao'}</div>
          </div>
        </div>
        ${isMerged
          ? `<span class="badge-type badge-success"><i class="fas fa-check-circle"></i> Đã hợp nhất</span>`
          : `<span class="badge-type badge-warning"><i class="fas fa-exclamation-circle"></i> Chờ xử lý</span>`
        }
      </div>

      <div class="dup-profiles">
        <div class="dup-profile">
          <div class="dup-profile-source">Nguồn: ${dup.profile1.source}</div>
          <div class="dup-profile-name">${dup.profile1.name}</div>
          <div class="dup-profile-phone"><i class="fas fa-phone" style="font-size:10px;margin-right:4px"></i>${dup.profile1.phone}</div>
          <div class="dup-profile-id">${dup.profile1.cdpId}</div>
          <div style="font-size:11px;color:var(--text-muted);margin-top:4px">${dup.profile1.email}</div>
        </div>
        <div class="dup-separator">
          <i class="fas fa-link"></i>
        </div>
        <div class="dup-profile">
          <div class="dup-profile-source">Nguồn: ${dup.profile2.source}</div>
          <div class="dup-profile-name">${dup.profile2.name}</div>
          <div class="dup-profile-phone"><i class="fas fa-phone" style="font-size:10px;margin-right:4px"></i>${dup.profile2.phone}</div>
          <div class="dup-profile-id">${dup.profile2.cdpId}</div>
          <div style="font-size:11px;color:var(--text-muted);margin-top:4px">${dup.profile2.email}</div>
        </div>
      </div>

      <div class="dup-reasons">
        <div class="dup-reasons-title"><i class="fas fa-info-circle" style="margin-right:4px"></i>Lý do trùng khớp</div>
        ${dup.matchReasons.map(r => `
          <div class="dup-reason-item">
            <i class="fas fa-check-circle"></i>
            ${r}
          </div>
        `).join('')}
      </div>

      ${!isMerged ? `
      <div class="dup-actions">
        <button class="btn btn-accent" onclick="openMergeModal('${dup.id}')">
          <i class="fas fa-compress-arrows-alt"></i> Merge Customer
        </button>
        <button class="btn btn-outline" onclick="dismissDup('${dup.id}')">
          <i class="fas fa-times"></i> Bỏ qua
        </button>
      </div>` : `
      <div class="dup-actions">
        <button class="btn btn-outline" disabled>
          <i class="fas fa-check"></i> Đã hợp nhất thành công
        </button>
      </div>`}
    </div>
  `;
}

function openMergeModal(dupId) {
  const dup = CDP_DATA.duplicateProfiles.find(d => d.id === dupId);
  if (!dup) return;

  const modal = document.getElementById('mergeModal');
  const c1 = dup.profile1;
  const c2 = dup.profile2;

  document.getElementById('mergeModalBody').innerHTML = `
    <div style="margin-bottom:16px">
      <div style="font-size:13px;color:var(--text-muted);margin-bottom:12px">
        Xem lại thông tin hai hồ sơ trước khi hợp nhất. Hệ thống sẽ tạo một Golden Record mới từ dữ liệu tốt nhất của cả hai hồ sơ.
      </div>
    </div>

    <div class="merge-preview">
      <div class="merge-profile">
        <div class="merge-profile-header">🔵 Hồ sơ nguồn 1 (${c1.source})</div>
        <div class="merge-field"><div class="merge-field-label">CDP ID</div><div class="merge-field-value font-mono">${c1.cdpId}</div></div>
        <div class="merge-field"><div class="merge-field-label">Họ tên</div><div class="merge-field-value">${c1.name}</div></div>
        <div class="merge-field"><div class="merge-field-label">SĐT</div><div class="merge-field-value">${c1.phone}</div></div>
        <div class="merge-field"><div class="merge-field-label">Email</div><div class="merge-field-value">${c1.email}</div></div>
      </div>
      <div class="merge-arrow"><i class="fas fa-plus"></i></div>
      <div class="merge-profile">
        <div class="merge-profile-header">🟡 Hồ sơ nguồn 2 (${c2.source})</div>
        <div class="merge-field"><div class="merge-field-label">CDP ID</div><div class="merge-field-value font-mono">${c2.cdpId}</div></div>
        <div class="merge-field"><div class="merge-field-label">Họ tên</div><div class="merge-field-value">${c2.name}</div></div>
        <div class="merge-field"><div class="merge-field-label">SĐT</div><div class="merge-field-value">${c2.phone}</div></div>
        <div class="merge-field"><div class="merge-field-label">Email</div><div class="merge-field-value">${c2.email}</div></div>
      </div>
    </div>

    <div style="text-align:center;margin:8px 0;font-size:20px;color:var(--primary)">⬇</div>

    <div class="merge-result">
      <div class="merge-result-header">✅ Golden Record sau hợp nhất</div>
      <div style="display:grid;grid-template-columns:1fr 1fr;gap:10px">
        <div class="merge-field"><div class="merge-field-label">CDP ID (Chủ)</div><div class="merge-field-value font-mono fw-700" style="color:var(--primary)">${c1.cdpId}</div></div>
        <div class="merge-field"><div class="merge-field-label">CDP ID (Phụ)</div><div class="merge-field-value font-mono" style="color:var(--text-muted);text-decoration:line-through">${c2.cdpId}</div></div>
        <div class="merge-field"><div class="merge-field-label">Họ tên</div><div class="merge-field-value fw-700">${c1.name}</div></div>
        <div class="merge-field"><div class="merge-field-label">SĐT</div><div class="merge-field-value">${c1.phone}</div></div>
        <div class="merge-field"><div class="merge-field-label">Email</div><div class="merge-field-value">${c1.email}</div></div>
        <div class="merge-field"><div class="merge-field-label">Nguồn dữ liệu</div><div class="merge-field-value">${c1.source} + ${c2.source}</div></div>
      </div>
    </div>

    <div style="background:rgba(245,166,35,.1);border:1px solid rgba(245,166,35,.3);border-radius:8px;padding:12px;margin-top:16px;font-size:12px;color:var(--text-secondary)">
      <i class="fas fa-exclamation-triangle" style="color:var(--accent);margin-right:6px"></i>
      Thao tác này không thể hoàn tác. Hồ sơ ${c2.cdpId} sẽ được hợp nhất vào ${c1.cdpId}.
    </div>
  `;

  document.getElementById('mergeConfirmBtn').onclick = () => confirmMerge(dupId);
  modal.classList.add('active');
}

function confirmMerge(dupId) {
  const dup = CDP_DATA.duplicateProfiles.find(d => d.id === dupId);
  if (!dup) return;

  dup.status = 'merged';
  CDP_DATA.stats.totalMerged++;

  closeMergeModal();

  // Re-render the card
  const cardEl = document.getElementById(`dup-${dupId}`);
  if (cardEl) {
    cardEl.outerHTML = renderDupCard(dup);
  }

  // Update dashboard stats
  document.getElementById('statMerged').textContent = CDP_DATA.stats.totalMerged;

  // Update nav badge
  const pendingCount = CDP_DATA.duplicateProfiles.filter(d => d.status === 'pending').length;
  const badge = document.querySelector('#nav-identity .nav-badge');
  if (badge) badge.textContent = pendingCount;

  showToast('✅ Hợp nhất hồ sơ thành công!', 'success');
}

function dismissDup(dupId) {
  const dup = CDP_DATA.duplicateProfiles.find(d => d.id === dupId);
  if (!dup) return;
  dup.status = 'dismissed';
  const cardEl = document.getElementById(`dup-${dupId}`);
  if (cardEl) cardEl.style.opacity = '.4';
  showToast('Đã bỏ qua hồ sơ trùng lặp', 'info');
}

function closeMergeModal() {
  document.getElementById('mergeModal').classList.remove('active');
}

// ============================================================
// TIMELINE
// ============================================================

function renderTimeline() {
  renderGlobalTimeline();
  setupTimelineFilter();
}

function renderGlobalTimeline(filter = null) {
  let events = [...CDP_DATA.events];

  if (filter && filter !== 'all') {
    events = events.filter(e => e.type === filter);
  }

  events = events.slice(0, 50);

  const container = document.getElementById('globalTimeline');
  if (!container) return;

  // Group by date
  const grouped = {};
  events.forEach(e => {
    if (!grouped[e.date]) grouped[e.date] = [];
    grouped[e.date].push(e);
  });

  container.innerHTML = Object.entries(grouped).map(([date, evts]) => {
    const d = new Date(date);
    const dateLabel = d.toLocaleDateString('vi-VN', { weekday: 'long', day: '2-digit', month: '2-digit', year: 'numeric' });
    return `
      <div style="margin-bottom:24px">
        <div style="font-size:12px;font-weight:700;color:var(--primary);text-transform:uppercase;letter-spacing:.5px;margin-bottom:12px;padding-bottom:8px;border-bottom:1px solid var(--border)">
          📅 ${dateLabel}
        </div>
        <div class="timeline">
          ${evts.map(e => {
            const customer = CDP_DATA.customers.find(c => c.id === e.customerId);
            const detail = formatEventDetail(e);
            return `
              <div class="timeline-item">
                <div class="timeline-dot" style="background:${e.color}">
                  <i class="fas ${e.icon}" style="font-size:8px"></i>
                </div>
                <div class="timeline-time">
                  ${new Date(e.timestamp).toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' })}
                  · <span style="color:var(--primary);font-size:10px">${e.source}</span>
                </div>
                <div class="timeline-content">
                  <div style="display:flex;align-items:center;justify-content:space-between;gap:8px">
                    <div class="timeline-label">${e.label}</div>
                    ${customer ? `
                    <span style="font-size:11px;color:var(--text-muted);cursor:pointer;text-decoration:underline" onclick="openCustomer360('${customer.id}')">
                      ${customer.name}
                    </span>` : ''}
                  </div>
                  ${detail ? `<div class="timeline-detail">${detail}</div>` : ''}
                </div>
              </div>
            `;
          }).join('')}
        </div>
      </div>
    `;
  }).join('');
}

function setupTimelineFilter() {
  const filterBtns = document.querySelectorAll('[data-timeline-filter]');
  filterBtns.forEach(btn => {
    btn.addEventListener('click', () => {
      filterBtns.forEach(b => b.classList.remove('active'));
      btn.classList.add('active');
      renderGlobalTimeline(btn.getAttribute('data-timeline-filter'));
    });
  });
}

// ============================================================
// SEGMENTS
// ============================================================

function renderSegments() {
  const container = document.getElementById('segmentGrid');
  if (!container) return;

  container.innerHTML = CDP_DATA.segments.map(seg => `
    <div class="segment-card" onclick="viewSegmentMembers('${seg.id}')" style="--card-color: ${seg.color}">
      <div style="position:absolute;top:0;left:0;right:0;height:4px;background:${seg.color}"></div>
      <div class="segment-icon" style="background: ${seg.color}22; color: ${seg.color}">
        <i class="fas ${seg.icon}"></i>
      </div>
      <div class="segment-card-name">${seg.name}</div>
      <div class="segment-card-desc">${seg.description}</div>
      <div>
        <div class="segment-card-count" style="color:${seg.color}">${seg.members.length}</div>
        <div class="segment-card-label">khách hàng</div>
      </div>
      <div class="segment-card-footer">
        <span style="font-size:11px;color:var(--text-muted)">
          <i class="fas fa-clock" style="margin-right:4px"></i>Cập nhật: ${seg.updatedAt}
        </span>
        <button class="btn btn-sm btn-outline" onclick="event.stopPropagation(); viewSegmentMembers('${seg.id}')">
          Xem thành viên <i class="fas fa-arrow-right"></i>
        </button>
      </div>
    </div>
  `).join('');
}

function viewSegmentMembers(segId) {
  const seg = CDP_DATA.segments.find(s => s.id === segId);
  if (!seg) return;

  // Switch to customers page with filter
  navigateTo('customers');

  const members = CDP_DATA.customers.filter(c => seg.members.includes(c.id));

  // Update header
  document.getElementById('headerTitle').textContent = `Segment: ${seg.name}`;
  document.getElementById('headerSub').textContent = `${members.length} thành viên`;

  // Clear search
  ['searchName', 'searchPhone', 'searchEmail', 'searchCdpId'].forEach(id => {
    const el = document.getElementById(id);
    if (el) el.value = '';
  });

  renderCustomerList(members, 1);

  // Add breadcrumb
  const tableHeader = document.querySelector('#page-customers .table-header');
  let bcEl = document.getElementById('segmentBreadcrumb');
  if (!bcEl) {
    bcEl = document.createElement('div');
    bcEl.id = 'segmentBreadcrumb';
    bcEl.style.cssText = 'padding:8px 20px;background:rgba(26,58,107,.05);border-bottom:1px solid var(--border);font-size:12px';
    tableHeader?.parentNode?.insertBefore(bcEl, tableHeader);
  }
  bcEl.innerHTML = `
    <span style="cursor:pointer;color:var(--primary)" onclick="clearSegmentFilter()">
      <i class="fas fa-arrow-left" style="margin-right:4px"></i>Tất cả khách hàng
    </span>
    <span style="margin:0 8px;color:var(--text-muted)">/</span>
    <span class="segment-tag" style="background:${seg.color}22;color:${seg.color};border:1px solid ${seg.color}44;display:inline-flex;align-items:center;gap:4px;padding:3px 10px;border-radius:99px;font-weight:600;font-size:11px">
      <i class="fas ${seg.icon}"></i> ${seg.name}
    </span>
    <span style="margin-left:8px;color:var(--text-muted)">${members.length} thành viên</span>
  `;
}

function clearSegmentFilter() {
  const bcEl = document.getElementById('segmentBreadcrumb');
  if (bcEl) bcEl.remove();
  renderCustomerList(CDP_DATA.customers, 1);
  document.getElementById('headerTitle').textContent = 'Quản lý Khách hàng';
  document.getElementById('headerSub').textContent = `${CDP_DATA.customers.length} khách hàng trong hệ thống`;
}

// ============================================================
// UTILITIES
// ============================================================

function getTypeBadge(type, customerType) {
  if (customerType === 'business') return `<span class="badge-type badge-business"><i class="fas fa-building" style="margin-right:3px"></i>Doanh nghiệp</span>`;
  if (type === 'VIP') return `<span class="badge-type badge-vip"><i class="fas fa-crown" style="margin-right:3px"></i>VIP</span>`;
  if (type === 'Thường xuyên') return `<span class="badge-type badge-frequent"><i class="fas fa-repeat" style="margin-right:3px"></i>Thường xuyên</span>`;
  return `<span class="badge-type badge-individual"><i class="fas fa-user" style="margin-right:3px"></i>Cá nhân</span>`;
}

function getDupAlertBadge(status) {
  if (status === 'merged') {
    return `<span class="badge-type badge-dup-merged"><i class="fas fa-check-circle"></i> Đã hợp nhất</span>`;
  }
  if (status === 'suspect') {
    return `<span class="badge-type badge-dup-suspect"><i class="fas fa-exclamation-triangle"></i> Nghi trùng</span>`;
  }
  return `<span class="badge-type badge-dup-normal"><i class="fas fa-check"></i> Bình thường</span>`;
}

function getActivityBadge(date) {
  const days = Math.floor((new Date() - new Date(date)) / (1000 * 60 * 60 * 24));
  if (days <= 7) return 'success';
  if (days <= 30) return 'warning';
  return 'danger';
}

function stringToColor(str) {
  const colors = [
    '#1a3a6b', '#2550a0', '#8b5cf6', '#ef4444', '#f59e0b',
    '#10b981', '#3b82f6', '#06b6d4', '#ec4899', '#84cc16',
  ];
  let hash = 0;
  for (let i = 0; i < str.length; i++) hash = str.charCodeAt(i) + ((hash << 5) - hash);
  return colors[Math.abs(hash) % colors.length];
}

function debounce(fn, delay) {
  let timer;
  return (...args) => {
    clearTimeout(timer);
    timer = setTimeout(() => fn(...args), delay);
  };
}

function showToast(message, type = 'info') {
  const toastContainer = document.getElementById('toastContainer');
  const toast = document.createElement('div');
  const colors = { success: '#10b981', danger: '#ef4444', warning: '#f59e0b', info: '#3b82f6' };
  toast.style.cssText = `
    background: #fff;
    border-left: 4px solid ${colors[type] || colors.info};
    border-radius: 8px;
    box-shadow: 0 4px 16px rgba(0,0,0,.12);
    padding: 12px 16px;
    font-size: 13px;
    font-weight: 500;
    color: var(--text-primary);
    animation: slideInRight .3s ease, fadeOut .4s ease 2.8s forwards;
    min-width: 240px;
  `;
  toast.textContent = message;
  toastContainer.appendChild(toast);
  setTimeout(() => toast.remove(), 3300);
}

// Add fadeOut animation
const style = document.createElement('style');
style.textContent = `
  @keyframes slideInRight {
    from { opacity: 0; transform: translateX(40px); }
    to { opacity: 1; transform: translateX(0); }
  }
  @keyframes fadeOut {
    from { opacity: 1; }
    to { opacity: 0; }
  }
`;
document.head.appendChild(style);

// Close modal on overlay click
document.addEventListener('click', (e) => {
  if (e.target.classList.contains('modal-overlay')) {
    e.target.classList.remove('active');
  }
});

// ============================================================
// CUSTOMER DUPLICATE ALERT
// ============================================================

function openCustomerDupModal(customerId) {
  const customer = CDP_DATA.customers.find(c => c.id === customerId);
  if (!customer || !customer.duplicatePairId) return;

  const dup = CDP_DATA.duplicateProfiles.find(d => d.id === customer.duplicatePairId);
  if (!dup) return;

  // Determine which profile this customer corresponds to
  const isBothSide = true;
  const c1 = dup.profile1;
  const c2 = dup.profile2;
  const score = dup.matchScore;
  const scoreClass = score >= 95 ? '#ef4444' : score >= 85 ? '#f59e0b' : '#3b82f6';

  // Attribute comparison rows
  const compareRows = [
    { attr: 'CDP ID', a: c1.cdpId, b: c2.cdpId, merged: c1.cdpId },
    { attr: 'Họ tên', a: c1.name, b: c2.name, merged: c1.name },
    { attr: 'SĐT', a: c1.phone, b: c2.phone, merged: c1.phone },
    { attr: 'Email', a: c1.email, b: c2.email, merged: c1.email },
    { attr: 'Địa chỉ', a: c1.address || '–', b: c2.address || '–', merged: c1.address || '–' },
    { attr: 'Nguồn dữ liệu', a: c1.source, b: c2.source, merged: `${c1.source} + ${c2.source}` },
  ];

  document.getElementById('customerDupModalBody').innerHTML = `
    <!-- Match Score + Profiles -->
    <div class="cdup-header">
      <div class="cdup-profiles">
        <div class="cdup-profile-box cdup-profile-a">
          <div class="cdup-profile-label">Hồ sơ hiện tại</div>
          <div class="cdup-profile-cdpid">${c1.cdpId}</div>
          <div class="cdup-profile-name">${c1.name}</div>
          <div class="cdup-profile-field"><i class="fas fa-phone"></i> ${c1.phone}</div>
          <div class="cdup-profile-field cdup-email">${c1.email}</div>
          <div class="cdup-source-badge">${c1.source}</div>
        </div>

        <div class="cdup-vs">
          <div class="cdup-score-circle" style="border-color:${scoreClass}">
            <div class="cdup-score-num" style="color:${scoreClass}">${score}%</div>
            <div class="cdup-score-label">Match</div>
          </div>
          <i class="fas fa-link" style="color:${scoreClass};font-size:14px;margin-top:8px"></i>
        </div>

        <div class="cdup-profile-box cdup-profile-b">
          <div class="cdup-profile-label">Hồ sơ nghi trùng</div>
          <div class="cdup-profile-cdpid">${c2.cdpId}</div>
          <div class="cdup-profile-name">${c2.name}</div>
          <div class="cdup-profile-field"><i class="fas fa-phone"></i> ${c2.phone}</div>
          <div class="cdup-profile-field cdup-email">${c2.email}</div>
          <div class="cdup-source-badge" style="background:#8b5cf620;color:#7c3aed;border-color:#8b5cf640">${c2.source}</div>
        </div>
      </div>

      <!-- Match Score Progress Bar -->
      <div class="cdup-score-bar-wrap">
        <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:6px">
          <span style="font-size:12px;font-weight:700;color:var(--text-primary)">Match Score</span>
          <span style="font-size:14px;font-weight:800;color:${scoreClass}">${score}%</span>
        </div>
        <div class="cdup-progress-track">
          <div class="cdup-progress-fill" style="width:${score}%;background:${scoreClass}"></div>
        </div>
        <div style="display:flex;justify-content:space-between;font-size:10px;color:var(--text-muted);margin-top:4px">
          <span>0%</span><span style="color:${score>=85?scoreClass:'var(--text-muted)'}">85%+: Cao</span><span>100%</span>
        </div>
      </div>

      <!-- Match Reasons -->
      <div class="cdup-reasons">
        <div class="cdup-reasons-title"><i class="fas fa-lightbulb"></i> Lý do khớp ( ${dup.matchReasons.length} yếu tố )</div>
        <div class="cdup-reasons-list">
          ${dup.matchReasons.map(r => `
            <div class="cdup-reason-item">
              <i class="fas fa-check-circle" style="color:var(--success)"></i>
              <span>${r}</span>
            </div>
          `).join('')}
        </div>
      </div>
    </div>

    <!-- Merge Preview Table -->
    <div style="margin-top:20px">
      <div style="font-size:13px;font-weight:700;color:var(--text-primary);margin-bottom:12px;display:flex;align-items:center;gap:8px">
        <i class="fas fa-table" style="color:var(--primary)"></i>
        Merge Preview – So sánh giá trị
      </div>
      <div style="overflow-x:auto">
        <table class="data-table cdup-compare-table">
          <thead>
            <tr>
              <th>Thuộc tính</th>
              <th style="color:#2563eb">Hồ sơ A (Hiện tại)</th>
              <th style="color:#7c3aed">Hồ sơ B (Nghi trùng)</th>
              <th style="color:var(--success)">Giá trị sau Merge</th>
            </tr>
          </thead>
          <tbody>
            ${compareRows.map(row => {
              const same = row.a === row.b;
              return `
                <tr>
                  <td style="font-weight:700;font-size:12px;color:var(--text-secondary)">${row.attr}</td>
                  <td style="font-size:12px;color:var(--text-primary)">${row.a}</td>
                  <td style="font-size:12px;color:var(--text-primary)">
                    ${same ? row.b : `<span style="background:rgba(245,158,11,.12);color:#d97706;padding:1px 5px;border-radius:4px">${row.b}</span>`}
                  </td>
                  <td style="font-size:12px;font-weight:600;color:var(--success)">
                    <i class="fas fa-check" style="margin-right:4px;font-size:10px"></i>${row.merged}
                  </td>
                </tr>
              `;
            }).join('')}
          </tbody>
        </table>
      </div>
    </div>

    <div style="margin-top:14px;padding:10px 14px;background:rgba(245,158,11,.08);border:1px solid rgba(245,158,11,.25);border-radius:8px;font-size:12px;color:var(--text-secondary)">
      <i class="fas fa-exclamation-triangle" style="color:#d97706;margin-right:6px"></i>
      Sau khi Merge, hồ sơ <strong>${c2.cdpId}</strong> sẽ được hợp nhất vào <strong>${c1.cdpId}</strong>. Thao tác này không thể hoàn tác.
    </div>
  `;

  document.getElementById('customerDupMergeBtn').onclick = () => confirmCustomerMerge(customer.id);
  document.getElementById('customerDupDismissBtn').onclick = () => dismissCustomerDup(customer.id);
  document.getElementById('customerDupModal').classList.add('active');
}

function closeCustomerDupModal() {
  document.getElementById('customerDupModal').classList.remove('active');
}

function confirmCustomerMerge(customerId) {
  const customer = CDP_DATA.customers.find(c => c.id === customerId);
  if (!customer || !customer.duplicatePairId) return;

  const dup = CDP_DATA.duplicateProfiles.find(d => d.id === customer.duplicatePairId);
  if (!dup || dup.status === 'merged') return;

  // Mark both customers as merged
  const primaryC = CDP_DATA.customers.find(c => c.id === dup.primaryCustomerId);
  const secondaryC = CDP_DATA.customers.find(c => c.id === dup.secondaryCustomerId);
  if (primaryC) primaryC.duplicateStatus = 'merged';
  if (secondaryC) secondaryC.duplicateStatus = 'merged';

  dup.status = 'merged';
  CDP_DATA.stats.totalMerged++;

  closeCustomerDupModal();

  // Update dashboard stats
  document.getElementById('statMerged').textContent = CDP_DATA.stats.totalMerged;
  const pendingPairs = CDP_DATA.duplicateProfiles.filter(d => d.status === 'pending').length;
  const suspectEl = document.getElementById('statSuspect');
  if (suspectEl) suspectEl.textContent = pendingPairs * 2;

  // Update sidebar identity badge
  const badge = document.querySelector('#nav-identity .nav-badge');
  if (badge) badge.textContent = pendingPairs;

  // Re-render the current page to update badges & buttons
  renderCustomerList(filteredCustomers, currentCustomerPage);

  // Also update the Identity Resolution card if rendered
  const dupCardEl = document.getElementById(`dup-${dup.id}`);
  if (dupCardEl) dupCardEl.outerHTML = renderDupCard(dup);

  showToast('✅ Merge Customer thành công.', 'success');
}

function dismissCustomerDup(customerId) {
  const customer = CDP_DATA.customers.find(c => c.id === customerId);
  if (!customer || !customer.duplicatePairId) return;

  const dup = CDP_DATA.duplicateProfiles.find(d => d.id === customer.duplicatePairId);
  if (dup) dup.status = 'dismissed';

  const primaryC = CDP_DATA.customers.find(c => c.id === dup.primaryCustomerId);
  const secondaryC = CDP_DATA.customers.find(c => c.id === dup.secondaryCustomerId);
  if (primaryC) primaryC.duplicateStatus = 'normal';
  if (secondaryC) secondaryC.duplicateStatus = 'normal';

  // Update suspect count
  const pendingPairs = CDP_DATA.duplicateProfiles.filter(d => d.status === 'pending').length;
  const suspectEl = document.getElementById('statSuspect');
  if (suspectEl) suspectEl.textContent = pendingPairs * 2;

  closeCustomerDupModal();
  renderCustomerList(filteredCustomers, currentCustomerPage);
  showToast('Đã bỏ qua cảnh báo trùng lặp.', 'info');
}

// ============================================================
// RULE MANAGEMENT
// ============================================================

const RULE_PAGE_SIZE = 10;
let ruleCurrentPage = 1;
let filteredRules = [];

function initRules() {
  filteredRules = [...RULES_DATA];
  renderRulesTable(filteredRules, 1);
  const activeCount = RULES_DATA.filter(r => r.status === 'Active').length;
  const el = document.getElementById('ruleActiveCount');
  if (el) el.textContent = activeCount;
}

function filterRules() {
  const keyword = (document.getElementById('ruleSearchKeyword')?.value || '').toLowerCase();
  const type = document.getElementById('ruleFilterType')?.value || '';
  const status = document.getElementById('ruleFilterStatus')?.value || '';

  filteredRules = RULES_DATA.filter(r => {
    const matchKeyword = !keyword || r.name.toLowerCase().includes(keyword) || r.description.toLowerCase().includes(keyword) || r.tags.some(t => t.includes(keyword));
    const matchType = !type || r.type === type;
    const matchStatus = !status || r.status === status;
    return matchKeyword && matchType && matchStatus;
  });

  renderRulesTable(filteredRules, 1);
}

function clearRuleFilters() {
  document.getElementById('ruleSearchKeyword').value = '';
  document.getElementById('ruleFilterType').value = '';
  document.getElementById('ruleFilterStatus').value = '';
  filterRules();
}

function renderRulesTable(rules, page = 1) {
  ruleCurrentPage = page;
  const start = (page - 1) * RULE_PAGE_SIZE;
  const end = start + RULE_PAGE_SIZE;
  const pageData = rules.slice(start, end);

  const countEl = document.getElementById('ruleCount');
  if (countEl) countEl.textContent = `${rules.length} rules`;

  const tbody = document.getElementById('ruleTableBody');
  if (!tbody) return;

  const ruleTypeMeta = {
    'Identity Resolution': { color: '#10b981', icon: 'fa-link', badgeClass: 'badge-success' },
    'Segment':             { color: '#8b5cf6', icon: 'fa-layer-group', badgeClass: 'badge-purple' },
    'Event Processing':    { color: '#3b82f6', icon: 'fa-bolt', badgeClass: 'badge-info' },
    'Behavior':            { color: '#f59e0b', icon: 'fa-brain', badgeClass: 'badge-warning' },
  };

  tbody.innerHTML = pageData.map((r, idx) => {
    const meta = ruleTypeMeta[r.type] || { color: '#64748b', icon: 'fa-cog', badgeClass: 'badge-info' };
    const isActive = r.status === 'Active';
    const globalIdx = start + idx + 1;
    return `
      <tr onclick="openRuleDetail('${r.id}')" style="cursor:pointer">
        <td><span style="font-size:11px;color:var(--text-muted);font-weight:600">${globalIdx}</span></td>
        <td>
          <div style="display:flex;align-items:center;gap:10px">
            <div style="width:34px;height:34px;border-radius:8px;background:${meta.color}18;color:${meta.color};display:flex;align-items:center;justify-content:center;font-size:13px;flex-shrink:0">
              <i class="fas ${meta.icon}"></i>
            </div>
            <div>
              <div style="font-weight:600;font-size:13px;color:var(--text-primary)">${r.name}</div>
              <div style="font-size:11px;color:var(--text-muted);margin-top:2px">ID: ${r.id}</div>
            </div>
          </div>
        </td>
        <td>
          <span class="badge-type ${meta.badgeClass}" style="display:inline-flex;align-items:center;gap:4px">
            <i class="fas ${meta.icon}" style="font-size:10px"></i>
            ${r.type}
          </span>
        </td>
        <td>
          <div style="font-size:12px;color:var(--text-secondary);max-width:260px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis" title="${r.description}">
            ${r.description}
          </div>
        </td>
        <td>
          <span class="rule-status-badge ${isActive ? 'rule-status-active' : 'rule-status-inactive'}">
            <span class="rule-status-dot"></span>
            ${r.status}
          </span>
        </td>
        <td style="text-align:center">
          <span style="display:inline-flex;align-items:center;justify-content:center;width:28px;height:28px;border-radius:50%;background:var(--bg-hover);border:1px solid var(--border);font-size:11px;font-weight:700;color:var(--text-secondary)">
            ${r.priority}
          </span>
        </td>
        <td>
          <span style="font-size:12px;color:var(--text-muted)">${r.createdDate}</span>
        </td>
        <td style="text-align:center">
          <div style="font-size:13px;font-weight:700;color:${meta.color}">${r.matchCount.toLocaleString('vi-VN')}</div>
          <div style="font-size:10px;color:var(--text-muted)">records</div>
        </td>
        <td>
          <div style="display:flex;gap:6px">
            <button class="btn btn-sm btn-outline" onclick="event.stopPropagation(); openRuleDetail('${r.id}')">
              <i class="fas fa-eye"></i> Xem
            </button>
            <button class="btn btn-sm" style="background:${isActive ? 'rgba(239,68,68,.1)' : 'rgba(16,185,129,.1)'};color:${isActive ? '#dc2626' : '#059669'};border:1px solid ${isActive ? 'rgba(239,68,68,.2)' : 'rgba(16,185,129,.2)'};border-radius:6px;padding:4px 8px;font-size:11px"
                onclick="event.stopPropagation(); toggleRuleStatus('${r.id}')">
              <i class="fas ${isActive ? 'fa-pause' : 'fa-play'}"></i> ${isActive ? 'Tắt' : 'Bật'}
            </button>
          </div>
        </td>
      </tr>
    `;
  }).join('') || '<tr><td colspan="9" class="text-center py-4 text-muted">Không tìm thấy Rule nào</td></tr>';

  renderRulePagination(rules.length, page);
}

function renderRulePagination(total, page) {
  const totalPages = Math.ceil(total / RULE_PAGE_SIZE);
  const container = document.getElementById('rulePagination');
  const info = document.getElementById('rulePaginationInfo');
  if (!container) return;

  const start = (page - 1) * RULE_PAGE_SIZE + 1;
  const end = Math.min(page * RULE_PAGE_SIZE, total);
  if (info) info.textContent = `Hiển thị ${start}–${end} / ${total}`;

  const prev = `<button class="page-btn" onclick="changeRulePage(${page - 1})" ${page === 1 ? 'disabled' : ''}><i class="fas fa-chevron-left"></i></button>`;
  const next = `<button class="page-btn" onclick="changeRulePage(${page + 1})" ${page === totalPages ? 'disabled' : ''}><i class="fas fa-chevron-right"></i></button>`;
  let pages = '';
  for (let i = 1; i <= totalPages; i++) {
    if (i === 1 || i === totalPages || (i >= page - 1 && i <= page + 1)) {
      pages += `<button class="page-btn ${i === page ? 'active' : ''}" onclick="changeRulePage(${i})">${i}</button>`;
    } else if (i === page - 2 || i === page + 2) {
      pages += `<span style="padding:0 4px;color:var(--text-muted)">…</span>`;
    }
  }
  container.innerHTML = prev + pages + next;
}

function changeRulePage(page) {
  const totalPages = Math.ceil(filteredRules.length / RULE_PAGE_SIZE);
  if (page < 1 || page > totalPages) return;
  renderRulesTable(filteredRules, page);
}

function toggleRuleStatus(ruleId) {
  const rule = RULES_DATA.find(r => r.id === ruleId);
  if (!rule) return;
  rule.status = rule.status === 'Active' ? 'Inactive' : 'Active';
  const activeCount = RULES_DATA.filter(r => r.status === 'Active').length;
  const el = document.getElementById('ruleActiveCount');
  if (el) el.textContent = activeCount;
  filterRules();
  showToast(`Rule "${rule.name}" đã được ${rule.status === 'Active' ? 'kích hoạt' : 'tắt'}`, rule.status === 'Active' ? 'success' : 'warning');
}

function openRuleDetail(ruleId) {
  const rule = RULES_DATA.find(r => r.id === ruleId);
  if (!rule) return;

  const ruleTypeMeta = {
    'Identity Resolution': { color: '#10b981', icon: 'fa-link' },
    'Segment':             { color: '#8b5cf6', icon: 'fa-layer-group' },
    'Event Processing':    { color: '#3b82f6', icon: 'fa-bolt' },
    'Behavior':            { color: '#f59e0b', icon: 'fa-brain' },
  };
  const meta = ruleTypeMeta[rule.type] || { color: '#64748b', icon: 'fa-cog' };
  const isActive = rule.status === 'Active';
  const lastRunStr = rule.lastRun ? new Date(rule.lastRun).toLocaleString('vi-VN') : 'N/A';

  const conditionRows = rule.conditions.map((cond, i) => `
    <div style="display:flex;align-items:center;gap:10px;padding:10px 14px;border-radius:8px;background:${i === 0 ? 'rgba(26,58,107,.05)' : 'rgba(100,116,139,.05)'};border:1px solid ${i === 0 ? 'rgba(26,58,107,.1)' : 'rgba(100,116,139,.08)'}">
      <span style="font-size:11px;font-weight:700;color:${i === 0 ? 'var(--primary)' : '#8b5cf6'};min-width:28px;text-transform:uppercase">${cond.logic}</span>
      <span style="font-size:12px;background:var(--bg-hover);padding:3px 10px;border-radius:6px;border:1px solid var(--border);font-family:monospace;color:var(--text-primary)">${cond.field}</span>
      <span style="font-size:12px;color:var(--primary);font-weight:600;font-family:monospace">${cond.operator}</span>
      <span style="font-size:12px;background:var(--bg-hover);padding:3px 10px;border-radius:6px;border:1px solid var(--border);font-family:monospace;color:var(--text-primary)">${cond.value}</span>
    </div>
  `).join('');

  document.getElementById('ruleDetailBody').innerHTML = `
    <!-- Header info -->
    <div style="display:flex;align-items:flex-start;gap:16px;margin-bottom:20px;padding:16px;background:linear-gradient(135deg,${meta.color}10,${meta.color}05);border-radius:10px;border:1px solid ${meta.color}20">
      <div style="width:52px;height:52px;border-radius:12px;background:${meta.color}20;color:${meta.color};display:flex;align-items:center;justify-content:center;font-size:20px;flex-shrink:0">
        <i class="fas ${meta.icon}"></i>
      </div>
      <div style="flex:1">
        <div style="font-size:16px;font-weight:800;color:var(--text-primary);margin-bottom:4px">${rule.name}</div>
        <div style="font-size:12px;color:var(--text-secondary);line-height:1.5">${rule.description}</div>
        <div style="display:flex;gap:8px;margin-top:8px;flex-wrap:wrap">
          ${rule.tags.map(t => `<span style="font-size:10px;padding:2px 8px;border-radius:99px;background:${meta.color}15;color:${meta.color};font-weight:600">#${t}</span>`).join('')}
        </div>
      </div>
      <span class="rule-status-badge ${isActive ? 'rule-status-active' : 'rule-status-inactive'}">
        <span class="rule-status-dot"></span> ${rule.status}
      </span>
    </div>

    <!-- Meta grid -->
    <div style="display:grid;grid-template-columns:1fr 1fr 1fr;gap:10px;margin-bottom:20px">
      <div style="padding:12px;border-radius:8px;background:var(--bg-hover);border:1px solid var(--border);text-align:center">
        <div style="font-size:11px;color:var(--text-muted);text-transform:uppercase;letter-spacing:.5px;margin-bottom:4px">Loại Rule</div>
        <div style="font-size:12px;font-weight:700;color:${meta.color}">${rule.type}</div>
      </div>
      <div style="padding:12px;border-radius:8px;background:var(--bg-hover);border:1px solid var(--border);text-align:center">
        <div style="font-size:11px;color:var(--text-muted);text-transform:uppercase;letter-spacing:.5px;margin-bottom:4px">Priority</div>
        <div style="font-size:20px;font-weight:800;color:var(--primary)">${rule.priority}</div>
      </div>
      <div style="padding:12px;border-radius:8px;background:var(--bg-hover);border:1px solid var(--border);text-align:center">
        <div style="font-size:11px;color:var(--text-muted);text-transform:uppercase;letter-spacing:.5px;margin-bottom:4px">Matched</div>
        <div style="font-size:16px;font-weight:800;color:${meta.color}">${rule.matchCount.toLocaleString('vi-VN')}</div>
      </div>
    </div>

    <!-- Rule Expression -->
    <div style="margin-bottom:16px">
      <div style="font-size:12px;font-weight:700;color:var(--text-primary);margin-bottom:10px;text-transform:uppercase;letter-spacing:.5px">
        <i class="fas fa-code" style="margin-right:6px;color:var(--primary)"></i>Biểu thức Rule
      </div>
      <div style="background:var(--primary);border-radius:8px;padding:14px 16px;font-family:monospace;font-size:13px;color:#e2e8f0;line-height:1.6;word-break:break-all">
        ${rule.ruleExpression}
      </div>
    </div>

    <!-- Conditions -->
    <div style="margin-bottom:16px">
      <div style="font-size:12px;font-weight:700;color:var(--text-primary);margin-bottom:10px;text-transform:uppercase;letter-spacing:.5px">
        <i class="fas fa-filter" style="margin-right:6px;color:var(--primary)"></i>Điều kiện Rule
      </div>
      <div style="display:flex;flex-direction:column;gap:8px">
        ${conditionRows}
      </div>
    </div>

    <!-- Action -->
    <div style="margin-bottom:16px">
      <div style="font-size:12px;font-weight:700;color:var(--text-primary);margin-bottom:10px;text-transform:uppercase;letter-spacing:.5px">
        <i class="fas fa-arrow-right" style="margin-right:6px;color:var(--primary)"></i>Hành động (THEN)
      </div>
      <div style="display:flex;align-items:center;gap:10px;padding:12px 16px;border-radius:8px;background:${rule.actionColor}12;border:1px solid ${rule.actionColor}25">
        <i class="fas fa-play-circle" style="color:${rule.actionColor};font-size:18px"></i>
        <span style="font-size:13px;font-weight:700;color:${rule.actionColor}">${rule.action}</span>
      </div>
    </div>

    <!-- Timeline info -->
    <div style="display:grid;grid-template-columns:1fr 1fr;gap:10px;padding-top:16px;border-top:1px solid var(--border)">
      <div style="font-size:12px;color:var(--text-muted)"><i class="fas fa-calendar-alt" style="margin-right:6px"></i>Ngày tạo: <strong style="color:var(--text-primary)">${rule.createdDate}</strong></div>
      <div style="font-size:12px;color:var(--text-muted)"><i class="fas fa-sync-alt" style="margin-right:6px"></i>Cập nhật: <strong style="color:var(--text-primary)">${rule.updatedDate}</strong></div>
      <div style="font-size:12px;color:var(--text-muted)" style="grid-column:span 2"><i class="fas fa-clock" style="margin-right:6px"></i>Chạy lần cuối: <strong style="color:var(--text-primary)">${lastRunStr}</strong></div>
    </div>
  `;

  document.getElementById('ruleDetailModal').classList.add('active');
}

function closeRuleModal() {
  document.getElementById('ruleDetailModal').classList.remove('active');
}

// ============================================================
// EVENT MANAGEMENT
// ============================================================

const EV_PAGE_SIZE = 20;
let evCurrentPage = 1;
let filteredEvents = [];
let _debounceFilterEvents = null;
function debounceFilterEvents() {
  if (!_debounceFilterEvents) _debounceFilterEvents = debounce(filterEvents, 300);
  _debounceFilterEvents();
}

function initEvents() {
  filteredEvents = [...EV_MANAGEMENT_DATA];

  // Populate filter dropdowns
  const typeSelect = document.getElementById('evFilterType');
  const scopeSelect = document.getElementById('evFilterScope');
  const sourceSelect = document.getElementById('evFilterSource');

  if (typeSelect) {
    EV_ALL_TYPES.forEach(t => {
      const meta = EV_TYPE_META[t];
      const opt = document.createElement('option');
      opt.value = t;
      opt.textContent = `${meta.label} (${t})`;
      typeSelect.appendChild(opt);
    });
  }

  if (scopeSelect) {
    const scopes = [...new Set(EV_MANAGEMENT_DATA.map(e => e.scope))].sort();
    scopes.forEach(s => {
      const opt = document.createElement('option');
      opt.value = s;
      opt.textContent = s;
      scopeSelect.appendChild(opt);
    });
  }

  if (sourceSelect) {
    const sources = [...new Set(EV_MANAGEMENT_DATA.map(e => e.source))].sort();
    sources.forEach(s => {
      const opt = document.createElement('option');
      opt.value = s;
      opt.textContent = s;
      sourceSelect.appendChild(opt);
    });
  }

  renderEvStatsRow();
  renderEvTable(filteredEvents, 1);
}

function renderEvStatsRow() {
  const container = document.getElementById('evStatsRow');
  if (!container) return;

  const counts = {};
  EV_MANAGEMENT_DATA.forEach(e => { counts[e.eventType] = (counts[e.eventType] || 0) + 1; });
  const top5 = Object.entries(counts).sort((a, b) => b[1] - a[1]).slice(0, 5);

  container.innerHTML = top5.map(([type, count]) => {
    const meta = EV_TYPE_META[type] || { label: type, color: '#64748b', icon: 'fa-bolt' };
    return `
      <div class="ev-stat-card" style="--ev-color:${meta.color}">
        <div class="ev-stat-icon" style="background:${meta.color}18;color:${meta.color}">
          <i class="fas ${meta.icon}"></i>
        </div>
        <div class="ev-stat-body">
          <div class="ev-stat-count" style="color:${meta.color}">${count}</div>
          <div class="ev-stat-label">${meta.label}</div>
        </div>
      </div>
    `;
  }).join('');
}

function filterEvents() {
  const keyword = (document.getElementById('evSearchKeyword')?.value || '').toLowerCase();
  const type = document.getElementById('evFilterType')?.value || '';
  const scope = document.getElementById('evFilterScope')?.value || '';
  const source = document.getElementById('evFilterSource')?.value || '';
  const timeRange = parseInt(document.getElementById('evFilterTimeRange')?.value || '0');

  const cutoff = timeRange ? new Date(Date.now() - timeRange * 86400000) : null;

  filteredEvents = EV_MANAGEMENT_DATA.filter(e => {
    const matchKeyword = !keyword || e.profileId.toLowerCase().includes(keyword) || e.sessionId.toLowerCase().includes(keyword) || e.source.toLowerCase().includes(keyword) || e.target.toLowerCase().includes(keyword) || e.eventType.toLowerCase().includes(keyword) || e.label.toLowerCase().includes(keyword);
    const matchType = !type || e.eventType === type;
    const matchScope = !scope || e.scope === scope;
    const matchSource = !source || e.source === source;
    const matchTime = !cutoff || new Date(e.time) >= cutoff;
    return matchKeyword && matchType && matchScope && matchSource && matchTime;
  });

  const countEl = document.getElementById('evTotalCount');
  if (countEl) countEl.textContent = filteredEvents.length;

  renderEvTable(filteredEvents, 1);
}

function clearEvFilters() {
  document.getElementById('evSearchKeyword').value = '';
  document.getElementById('evFilterType').value = '';
  document.getElementById('evFilterScope').value = '';
  document.getElementById('evFilterSource').value = '';
  document.getElementById('evFilterTimeRange').value = '';
  filterEvents();
}

function renderEvTable(events, page = 1) {
  evCurrentPage = page;
  const start = (page - 1) * EV_PAGE_SIZE;
  const end = start + EV_PAGE_SIZE;
  const pageData = events.slice(start, end);

  const countEl = document.getElementById('evCount');
  if (countEl) countEl.textContent = `${events.length} events`;

  const tbody = document.getElementById('evTableBody');
  if (!tbody) return;

  tbody.innerHTML = pageData.map(e => {
    const meta = EV_TYPE_META[e.eventType] || { color: '#64748b', icon: 'fa-bolt', label: e.eventType };
    return `
      <tr>
        <td>
          <div style="font-size:12px;color:var(--text-secondary);white-space:nowrap">${new Date(e.time).toLocaleString('en-US', {year:'numeric',month:'numeric',day:'numeric',hour:'numeric',minute:'2-digit',second:'2-digit'})}</div>
        </td>
        <td>
          <span class="ev-type-badge" style="background:${meta.color}18;color:${meta.color};border:1px solid ${meta.color}30">
            <i class="fas ${meta.icon}" style="font-size:10px"></i>
            ${e.eventType}
          </span>
        </td>
        <td>
          <span class="font-mono" style="font-size:11px;color:var(--primary)">${e.profileId}</span>
        </td>
        <td>
          <span class="font-mono" style="font-size:10px;color:var(--text-muted);max-width:180px;display:inline-block;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;vertical-align:middle" title="${e.sessionId}">${e.sessionId}</span>
        </td>
        <td>
          <span style="font-size:11px;padding:2px 8px;border-radius:4px;background:rgba(26,58,107,.08);color:var(--primary-light);font-weight:500">${e.source}</span>
        </td>
        <td>
          <span style="font-size:11px;color:var(--text-secondary);max-width:160px;display:inline-block;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;vertical-align:middle" title="${e.target}">${e.target}</span>
        </td>
        <td>
          <span style="font-size:11px;color:var(--text-muted)">${e.scope}</span>
        </td>
        <td>
          <button class="btn btn-sm btn-outline" onclick="openEvDrawer('${e.id}')">
            <i class="fas fa-eye"></i> View
          </button>
        </td>
      </tr>
    `;
  }).join('') || '<tr><td colspan="8" class="text-center py-4 text-muted">Không có sự kiện nào</td></tr>';

  renderEvPagination(events.length, page);
}

function renderEvPagination(total, page) {
  const totalPages = Math.ceil(total / EV_PAGE_SIZE);
  const container = document.getElementById('evPagination');
  const info = document.getElementById('evPaginationInfo');
  if (!container) return;

  const start = (page - 1) * EV_PAGE_SIZE + 1;
  const end = Math.min(page * EV_PAGE_SIZE, total);
  if (info) info.textContent = `Hiển thị ${start}–${end} / ${total}`;

  const prev = `<button class="page-btn" onclick="changeEvPage(${page - 1})" ${page === 1 ? 'disabled' : ''}><i class="fas fa-chevron-left"></i></button>`;
  const next = `<button class="page-btn" onclick="changeEvPage(${page + 1})" ${page === totalPages ? 'disabled' : ''}><i class="fas fa-chevron-right"></i></button>`;
  let pages = '';
  for (let i = 1; i <= totalPages; i++) {
    if (i === 1 || i === totalPages || (i >= page - 1 && i <= page + 1)) {
      pages += `<button class="page-btn ${i === page ? 'active' : ''}" onclick="changeEvPage(${i})">${i}</button>`;
    } else if (i === page - 2 || i === page + 2) {
      pages += `<span style="padding:0 4px;color:var(--text-muted)">…</span>`;
    }
  }
  container.innerHTML = prev + pages + next;
}

function changeEvPage(page) {
  const totalPages = Math.ceil(filteredEvents.length / EV_PAGE_SIZE);
  if (page < 1 || page > totalPages) return;
  renderEvTable(filteredEvents, page);
}

function openEvDrawer(eventId) {
  const ev = EV_MANAGEMENT_DATA.find(e => e.id === eventId);
  if (!ev) return;

  const meta = EV_TYPE_META[ev.eventType] || { color: '#64748b', icon: 'fa-bolt', label: ev.eventType };
  const timeStr = new Date(ev.time).toLocaleString('en-US', { year: 'numeric', month: 'numeric', day: 'numeric', hour: 'numeric', minute: '2-digit', second: '2-digit' });

  const payloadJson = JSON.stringify(ev.payload, null, 2);
  const highlighted = payloadJson
    .replace(/"([^"]+)":/g, '<span style="color:#93c5fd">"$1"</span>:')
    .replace(/: "([^"]+)"/g, ': <span style="color:#86efac">"$1"</span>')
    .replace(/: (\d+)/g, ': <span style="color:#fcd34d">$1</span>')
    .replace(/: (true|false|null)/g, ': <span style="color:#f9a8d4">$1</span>');

  document.getElementById('evDrawerBody').innerHTML = `
    <!-- Event type header -->
    <div style="display:flex;align-items:center;gap:12px;padding:16px;background:linear-gradient(135deg,${meta.color}12,${meta.color}06);border-radius:10px;margin-bottom:20px;border:1px solid ${meta.color}20">
      <div style="width:48px;height:48px;border-radius:12px;background:${meta.color}22;color:${meta.color};display:flex;align-items:center;justify-content:center;font-size:20px;flex-shrink:0">
        <i class="fas ${meta.icon}"></i>
      </div>
      <div>
        <div style="font-size:15px;font-weight:800;color:${meta.color}">${ev.eventType}</div>
        <div style="font-size:12px;color:var(--text-secondary)">${meta.label}</div>
      </div>
      <span class="ev-type-badge" style="margin-left:auto;background:${meta.color}15;color:${meta.color};border:1px solid ${meta.color}25">
        ${ev.id}
      </span>
    </div>

    <!-- Basic Information -->
    <div style="margin-bottom:20px">
      <div style="font-size:11px;font-weight:700;text-transform:uppercase;letter-spacing:.8px;color:var(--text-muted);margin-bottom:12px;display:flex;align-items:center;gap:6px">
        <i class="fas fa-info-circle" style="color:var(--primary)"></i> Basic Information
      </div>
      <div style="display:flex;flex-direction:column;gap:1px;border-radius:8px;overflow:hidden;border:1px solid var(--border)">
        ${[
          ['Event ID', ev.id, 'fa-fingerprint'],
          ['Event Type', `<span style="color:${meta.color};font-weight:700">${ev.eventType}</span>`, 'fa-bolt'],
          ['Time', timeStr, 'fa-clock'],
          ['Profile', `<span class="font-mono" style="color:var(--primary)">${ev.profileId}</span>`, 'fa-user'],
          ['Session', `<span class="font-mono" style="font-size:11px;color:var(--text-muted)">${ev.sessionId}</span>`, 'fa-route'],
          ['Source', `<span style="font-weight:600;color:var(--primary-light)">${ev.source}</span>`, 'fa-database'],
          ['Target', `<span style="color:var(--text-secondary)">${ev.target}</span>`, 'fa-crosshairs'],
          ['Scope', `<span style="color:var(--text-secondary)">${ev.scope}</span>`, 'fa-globe'],
        ].map(([label, val, icon]) => `
          <div style="display:flex;align-items:flex-start;gap:12px;padding:10px 14px;background:var(--bg-card);border-bottom:1px solid var(--border-light)">
            <div style="width:28px;height:28px;border-radius:6px;background:rgba(26,58,107,.06);color:var(--primary);display:flex;align-items:center;justify-content:center;font-size:11px;flex-shrink:0">
              <i class="fas ${icon}"></i>
            </div>
            <div style="flex:1">
              <div style="font-size:10px;text-transform:uppercase;letter-spacing:.5px;color:var(--text-muted);margin-bottom:2px">${label}</div>
              <div style="font-size:12px;color:var(--text-primary)">${val}</div>
            </div>
          </div>
        `).join('')}
      </div>
    </div>

    <!-- Payload -->
    <div>
      <div style="font-size:11px;font-weight:700;text-transform:uppercase;letter-spacing:.8px;color:var(--text-muted);margin-bottom:12px;display:flex;align-items:center;gap:6px;justify-content:space-between">
        <div style="display:flex;align-items:center;gap:6px">
          <i class="fas fa-code" style="color:var(--primary)"></i> Payload
        </div>
        <button class="btn btn-sm btn-outline" onclick="copyPayload('${ev.id}')" style="font-size:10px">
          <i class="fas fa-copy"></i> Copy
        </button>
      </div>
      <div style="background:#0f172a;border-radius:8px;padding:16px;font-family:monospace;font-size:12px;line-height:1.7;overflow-x:auto;white-space:pre;color:#e2e8f0;max-height:300px;overflow-y:auto" id="payload-${ev.id}">${highlighted}</div>
    </div>
  `;

  document.getElementById('evDrawer').classList.add('open');
  document.getElementById('evDrawerOverlay').classList.add('active');
}

function closeEvDrawer() {
  document.getElementById('evDrawer').classList.remove('open');
  document.getElementById('evDrawerOverlay').classList.remove('active');
}

function copyPayload(eventId) {
  const ev = EV_MANAGEMENT_DATA.find(e => e.id === eventId);
  if (!ev) return;
  navigator.clipboard.writeText(JSON.stringify(ev.payload, null, 2)).then(() => {
    showToast('Đã copy payload vào clipboard!', 'success');
  }).catch(() => {
    showToast('Không thể copy, vui lòng copy thủ công', 'warning');
  });
}
