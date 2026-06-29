// ============================================================
// CDP VNPost - Rule Management & Event Management Mock Data
// ============================================================

// ============================================================
// RULE MANAGEMENT DATA
// ============================================================

const RULE_TYPES = ['Identity Resolution', 'Segment', 'Event Processing', 'Behavior'];

const RULES_DATA = [
  {
    id: 'RULE001',
    name: 'Identity Match by Phone',
    type: 'Identity Resolution',
    description: 'Match khách hàng theo số điện thoại – nếu trùng SĐT thì coi là cùng một người',
    status: 'Active',
    priority: 1,
    createdDate: '2024-01-10',
    updatedDate: '2024-06-01',
    conditions: [
      { field: 'Phone', operator: '=', value: 'Phone', logic: 'IF' },
      { field: 'Email', operator: '=', value: 'Email', logic: 'OR' },
    ],
    action: 'Merge Customer',
    actionColor: '#10b981',
    ruleExpression: 'IF (Phone = Phone) OR (Email = Email) THEN Merge Customer',
    tags: ['phone', 'email', 'merge'],
    matchCount: 1284,
    lastRun: '2026-06-25T08:30:00Z',
  },
  {
    id: 'RULE002',
    name: 'Segment VIP Customer',
    type: 'Segment',
    description: 'Xếp khách hàng vào nhóm VIP nếu doanh thu vượt 20 triệu đồng hoặc có trên 50 đơn hàng',
    status: 'Active',
    priority: 2,
    createdDate: '2024-01-15',
    updatedDate: '2024-05-20',
    conditions: [
      { field: 'TotalSpent', operator: '>=', value: '20,000,000 VND', logic: 'IF' },
      { field: 'TotalOrders', operator: '>=', value: '50', logic: 'OR' },
    ],
    action: 'Add to Segment [VIP]',
    actionColor: '#f59e0b',
    ruleExpression: 'IF (TotalSpent >= 20000000) OR (TotalOrders >= 50) THEN AddToSegment("VIP")',
    tags: ['vip', 'segment', 'revenue'],
    matchCount: 843,
    lastRun: '2026-06-25T09:00:00Z',
  },
  {
    id: 'RULE003',
    name: 'Churn Risk Detection',
    type: 'Behavior',
    description: 'Phát hiện khách hàng có nguy cơ rời bỏ – không phát sinh giao dịch trong 30 ngày',
    status: 'Active',
    priority: 3,
    createdDate: '2024-02-01',
    updatedDate: '2024-06-10',
    conditions: [
      { field: 'DaysSinceLastOrder', operator: '>=', value: '30', logic: 'IF' },
      { field: 'TotalOrders', operator: '>=', value: '3', logic: 'AND' },
    ],
    action: 'Tag as Churn Risk',
    actionColor: '#ef4444',
    ruleExpression: 'IF (DaysSinceLastOrder >= 30) AND (TotalOrders >= 3) THEN Tag("churn-risk")',
    tags: ['churn', 'risk', 'behavior'],
    matchCount: 321,
    lastRun: '2026-06-25T07:00:00Z',
  },
  {
    id: 'RULE004',
    name: 'Identity Match by Email',
    type: 'Identity Resolution',
    description: 'Match khách hàng theo email – không phân biệt hoa/thường',
    status: 'Active',
    priority: 4,
    createdDate: '2024-01-20',
    updatedDate: '2024-04-15',
    conditions: [
      { field: 'Email (normalized)', operator: '=', value: 'Email (normalized)', logic: 'IF' },
    ],
    action: 'Merge Customer',
    actionColor: '#10b981',
    ruleExpression: 'IF (normalize(Email) = normalize(Email)) THEN Merge Customer',
    tags: ['email', 'identity', 'merge'],
    matchCount: 567,
    lastRun: '2026-06-25T08:00:00Z',
  },
  {
    id: 'RULE005',
    name: 'New Customer Welcome Event',
    type: 'Event Processing',
    description: 'Kích hoạt sự kiện chào mừng khi khách hàng đăng ký lần đầu',
    status: 'Active',
    priority: 5,
    createdDate: '2024-03-01',
    updatedDate: '2024-05-01',
    conditions: [
      { field: 'EventType', operator: '=', value: 'register', logic: 'IF' },
      { field: 'CustomerAge', operator: '=', value: '0 days', logic: 'AND' },
    ],
    action: 'Trigger Welcome Campaign',
    actionColor: '#8b5cf6',
    ruleExpression: 'IF (EventType = "register") AND (CustomerAge = 0) THEN TriggerCampaign("welcome")',
    tags: ['event', 'welcome', 'register'],
    matchCount: 2140,
    lastRun: '2026-06-25T10:00:00Z',
  },
  {
    id: 'RULE006',
    name: 'Business Customer Segment',
    type: 'Segment',
    description: 'Tự động phân khúc tất cả khách hàng loại doanh nghiệp (B2B)',
    status: 'Active',
    priority: 6,
    createdDate: '2024-01-05',
    updatedDate: '2024-03-20',
    conditions: [
      { field: 'CustomerType', operator: '=', value: 'business', logic: 'IF' },
    ],
    action: 'Add to Segment [Business]',
    actionColor: '#3b82f6',
    ruleExpression: 'IF (CustomerType = "business") THEN AddToSegment("Business")',
    tags: ['business', 'b2b', 'segment'],
    matchCount: 20,
    lastRun: '2026-06-24T23:00:00Z',
  },
  {
    id: 'RULE007',
    name: 'High Value Order Tracking',
    type: 'Event Processing',
    description: 'Tự động gắn tag đơn hàng giá trị cao khi thanh toán trên 10 triệu đồng',
    status: 'Active',
    priority: 7,
    createdDate: '2024-04-01',
    updatedDate: '2024-06-05',
    conditions: [
      { field: 'EventType', operator: '=', value: 'payment', logic: 'IF' },
      { field: 'Amount', operator: '>=', value: '10,000,000 VND', logic: 'AND' },
    ],
    action: 'Tag Order as High Value',
    actionColor: '#f59e0b',
    ruleExpression: 'IF (EventType = "payment") AND (Amount >= 10000000) THEN Tag("high-value-order")',
    tags: ['payment', 'high-value', 'event'],
    matchCount: 432,
    lastRun: '2026-06-25T09:30:00Z',
  },
  {
    id: 'RULE008',
    name: 'Inactive Customer Flag',
    type: 'Behavior',
    description: 'Đánh dấu khách hàng không hoạt động sau 90 ngày không có giao dịch',
    status: 'Inactive',
    priority: 8,
    createdDate: '2024-02-15',
    updatedDate: '2024-05-10',
    conditions: [
      { field: 'DaysSinceLastActivity', operator: '>=', value: '90', logic: 'IF' },
    ],
    action: 'Set Status = Inactive',
    actionColor: '#6b7280',
    ruleExpression: 'IF (DaysSinceLastActivity >= 90) THEN SetStatus("inactive")',
    tags: ['inactive', 'behavior', 'dormant'],
    matchCount: 178,
    lastRun: '2026-06-20T06:00:00Z',
  },
  {
    id: 'RULE009',
    name: 'Phone Number Normalization',
    type: 'Identity Resolution',
    description: 'Chuẩn hóa số điện thoại về định dạng 10 số trước khi match identity',
    status: 'Active',
    priority: 9,
    createdDate: '2024-01-25',
    updatedDate: '2024-04-01',
    conditions: [
      { field: 'Phone', operator: 'matches', value: '^(0|\\+84)', logic: 'IF' },
    ],
    action: 'Normalize Phone to E.164',
    actionColor: '#06b6d4',
    ruleExpression: 'IF (Phone matches "^(0|+84)") THEN Normalize(Phone, "E.164")',
    tags: ['phone', 'normalize', 'identity'],
    matchCount: 5621,
    lastRun: '2026-06-25T00:00:00Z',
  },
  {
    id: 'RULE010',
    name: 'Cart Abandonment Detection',
    type: 'Behavior',
    description: 'Phát hiện khách hàng thêm vào giỏ hàng nhưng không thanh toán trong 2 giờ',
    status: 'Active',
    priority: 10,
    createdDate: '2024-05-01',
    updatedDate: '2024-06-15',
    conditions: [
      { field: 'EventType', operator: '=', value: 'addToCart', logic: 'IF' },
      { field: 'TimeSinceEvent', operator: '>=', value: '2 hours', logic: 'AND' },
      { field: 'HasPaymentEvent', operator: '=', value: 'false', logic: 'AND' },
    ],
    action: 'Tag as Cart Abandoner',
    actionColor: '#ec4899',
    ruleExpression: 'IF (EventType = "addToCart") AND (TimeSince >= 2h) AND (NOT HasPayment) THEN Tag("cart-abandoner")',
    tags: ['cart', 'abandonment', 'ecommerce'],
    matchCount: 892,
    lastRun: '2026-06-25T10:15:00Z',
  },
  {
    id: 'RULE011',
    name: 'Repeat Buyer Promotion',
    type: 'Segment',
    description: 'Phân khúc khách hàng mua lặp lại nhiều lần để áp dụng chương trình khuyến mãi',
    status: 'Active',
    priority: 11,
    createdDate: '2024-03-15',
    updatedDate: '2024-05-25',
    conditions: [
      { field: 'TotalOrders', operator: '>=', value: '10', logic: 'IF' },
      { field: 'OrderFrequency', operator: '>=', value: '1 per month', logic: 'AND' },
    ],
    action: 'Add to Segment [Loyal Buyer]',
    actionColor: '#8b5cf6',
    ruleExpression: 'IF (TotalOrders >= 10) AND (FreqOrders >= 1/month) THEN AddToSegment("loyal-buyer")',
    tags: ['loyalty', 'promotion', 'segment'],
    matchCount: 456,
    lastRun: '2026-06-24T20:00:00Z',
  },
  {
    id: 'RULE012',
    name: 'Shipment Return Behavior',
    type: 'Event Processing',
    description: 'Ghi nhận và phân tích hành vi trả hàng của khách hàng',
    status: 'Inactive',
    priority: 12,
    createdDate: '2024-04-20',
    updatedDate: '2024-06-01',
    conditions: [
      { field: 'EventType', operator: '=', value: 'shipmentReturned', logic: 'IF' },
      { field: 'ReturnCount', operator: '>=', value: '3', logic: 'AND' },
    ],
    action: 'Flag for Review',
    actionColor: '#ef4444',
    ruleExpression: 'IF (EventType = "shipmentReturned") AND (ReturnCount >= 3) THEN FlagForReview()',
    tags: ['return', 'shipment', 'review'],
    matchCount: 89,
    lastRun: '2026-06-22T12:00:00Z',
  },
];

// ============================================================
// EVENT MANAGEMENT - EXTENDED DATA (300 events)
// ============================================================

const EV_PROFILES = [
  'profile-vip-001', 'profile-vip-002', 'profile-vip-003',
  'profile-churn-risk-1', 'profile-churn-risk-2', 'profile-churn-risk-3',
  'profile-new-001', 'profile-new-002', 'profile-new-003', 'profile-new-004',
  'profile-biz-001', 'profile-biz-002', 'profile-biz-003',
  'profile-regular-001', 'profile-regular-002', 'profile-regular-003',
  'profile-regular-004', 'profile-regular-005',
];

const EV_SCOPES = [
  'my-vnpost-app', 'vnpost-web', 'crm-system', 'cms-platform',
  'ecommerce-portal', 'tracking-app', 'payment-gateway',
];

const EV_SOURCES_LIST = [
  'site:ecommerce-phone', 'site:ecommerce-laptop', 'site:tracking-web',
  'app:myvnpost-ios', 'app:myvnpost-android', 'system:crm',
  'system:cms', 'system:payment-gw', 'api:partner-integration',
];

const EV_TARGETS = {
  addToCart: ['product:iphone-15-pro', 'product:samsung-s24', 'product:laptop-dell', 'product:airpods-pro', 'product:macbook-air'],
  removeFromCart: ['product:iphone-15-pro', 'product:samsung-s24', 'product:laptop-dell'],
  viewProduct: ['product:iphone-15-pro', 'product:tv-samsung-55', 'product:airpods-pro', 'product:macbook-air', 'product:ipad-pro'],
  search: ['query:dien-thoai', 'query:laptop', 'query:tai-nghe', 'query:may-tinh-bang', 'query:smart-watch'],
  login: ['platform:myvnpost', 'platform:web-portal', 'platform:crm'],
  logout: ['platform:mywnpost', 'platform:web-portal'],
  createOrder: ['order:' + Math.random().toString(36).substring(2, 12)],
  payment: ['invoice:' + Math.random().toString(36).substring(2, 10)],
  shipmentCreated: ['shipment:VN' + Math.floor(Math.random()*1e9) + 'VN'],
  shipmentDelivered: ['shipment:VN' + Math.floor(Math.random()*1e9) + 'VN'],
  shipmentReturned: ['shipment:VN' + Math.floor(Math.random()*1e9) + 'VN'],
  trackShipment: ['shipment:VN' + Math.floor(Math.random()*1e9) + 'VN'],
  updateProfile: ['field:phone', 'field:email', 'field:address', 'field:name'],
};

const EV_TYPE_META = {
  login:             { label: 'Đăng nhập',           icon: 'fa-sign-in-alt',     color: '#3b82f6',  badgeClass: 'ev-badge-login' },
  logout:            { label: 'Đăng xuất',            icon: 'fa-sign-out-alt',    color: '#64748b',  badgeClass: 'ev-badge-logout' },
  createOrder:       { label: 'Tạo đơn hàng',         icon: 'fa-box',             color: '#10b981',  badgeClass: 'ev-badge-create-order' },
  payment:           { label: 'Thanh toán',            icon: 'fa-credit-card',     color: '#8b5cf6',  badgeClass: 'ev-badge-payment' },
  shipmentCreated:   { label: 'Tạo bưu gửi',          icon: 'fa-dolly',           color: '#06b6d4',  badgeClass: 'ev-badge-shipment-created' },
  shipmentDelivered: { label: 'Giao hàng thành công', icon: 'fa-check-circle',    color: '#10b981',  badgeClass: 'ev-badge-delivered' },
  shipmentReturned:  { label: 'Hoàn trả hàng',        icon: 'fa-undo-alt',        color: '#ef4444',  badgeClass: 'ev-badge-returned' },
  trackShipment:     { label: 'Theo dõi bưu gửi',     icon: 'fa-truck',           color: '#f59e0b',  badgeClass: 'ev-badge-track' },
  addToCart:         { label: 'Thêm vào giỏ hàng',    icon: 'fa-cart-plus',       color: '#ec4899',  badgeClass: 'ev-badge-add-cart' },
  removeFromCart:    { label: 'Xóa khỏi giỏ hàng',   icon: 'fa-cart-arrow-down', color: '#f87171',  badgeClass: 'ev-badge-remove-cart' },
  search:            { label: 'Tìm kiếm',              icon: 'fa-search',          color: '#a78bfa',  badgeClass: 'ev-badge-search' },
  viewProduct:       { label: 'Xem sản phẩm',         icon: 'fa-eye',             color: '#6b7280',  badgeClass: 'ev-badge-view' },
  updateProfile:     { label: 'Cập nhật hồ sơ',       icon: 'fa-user-edit',       color: '#14b8a6',  badgeClass: 'ev-badge-update' },
};

const EV_ALL_TYPES = Object.keys(EV_TYPE_META);

function evRandomFrom(arr) {
  return arr[Math.floor(Math.random() * arr.length)];
}

function evRandomInt(min, max) {
  return Math.floor(Math.random() * (max - min + 1)) + min;
}

function evRandomDate(daysAgo = 30) {
  const d = new Date();
  d.setDate(d.getDate() - evRandomInt(0, daysAgo));
  d.setHours(evRandomInt(0, 23), evRandomInt(0, 59), evRandomInt(0, 59));
  return d;
}

function generateEvPayload(type) {
  switch (type) {
    case 'addToCart':
    case 'removeFromCart':
      return { productId: evRandomFrom(['iphone-15-pro', 'samsung-s24', 'laptop-dell', 'airpods-pro', 'macbook-air']), price: evRandomInt(1000000, 35000000), quantity: evRandomInt(1, 3), currency: 'VND' };
    case 'viewProduct':
      return { productId: evRandomFrom(['iphone-15-pro', 'tv-samsung-55', 'airpods-pro', 'macbook-air', 'ipad-pro']), duration: evRandomInt(5, 300), referrer: evRandomFrom(['homepage', 'search', 'category', 'recommendation']) };
    case 'search':
      return { keyword: evRandomFrom(['điện thoại', 'laptop', 'tai nghe', 'máy tính bảng', 'đồng hồ thông minh', 'camera', 'tivi']), resultCount: evRandomInt(0, 200), filters: evRandomFrom([null, { category: 'electronics' }, { priceRange: '5-10tr' }]) };
    case 'login':
      return { device: evRandomFrom(['Mobile', 'Desktop', 'Tablet']), os: evRandomFrom(['iOS', 'Android', 'Windows', 'macOS']), ip: `${evRandomInt(1,254)}.${evRandomInt(1,254)}.${evRandomInt(1,254)}.${evRandomInt(1,254)}`, method: evRandomFrom(['password', 'OTP', 'biometric', 'SSO']) };
    case 'logout':
      return { sessionDuration: evRandomInt(60, 7200), device: evRandomFrom(['Mobile', 'Desktop']) };
    case 'createOrder':
      return { orderId: `ORD${evRandomInt(100000, 999999)}`, amount: evRandomInt(50000, 10000000), items: evRandomInt(1, 5), service: evRandomFrom(['Chuyển phát nhanh', 'Bưu phẩm thường', 'EMS', 'Bưu phẩm quốc tế']), destination: evRandomFrom(['Hà Nội', 'TP. Hồ Chí Minh', 'Đà Nẵng', 'Cần Thơ']) };
    case 'payment':
      return { amount: evRandomInt(50000, 10000000), currency: 'VND', method: evRandomFrom(['Tiền mặt', 'Chuyển khoản', 'Ví MoMo', 'ZaloPay', 'VNPay', 'Thẻ ATM', 'Thẻ tín dụng']), transactionId: `TXN${evRandomInt(100000000, 999999999)}`, status: 'success' };
    case 'shipmentCreated':
      return { trackingNumber: `VN${evRandomInt(100000000, 999999999)}VN`, weight: evRandomInt(100, 30000), service: evRandomFrom(['Chuyển phát nhanh', 'EMS', 'Bưu phẩm thường']), sender: evRandomFrom(['Hà Nội', 'TP.HCM']), recipient: evRandomFrom(['Đà Nẵng', 'Cần Thơ', 'Hải Phòng']) };
    case 'shipmentDelivered':
      return { trackingNumber: `VN${evRandomInt(100000000, 999999999)}VN`, deliveredAt: new Date(Date.now() - evRandomInt(0, 86400000)).toISOString(), signedBy: evRandomFrom(['chủ hộ', 'người thân', 'bảo vệ', 'đồng nghiệp']) };
    case 'shipmentReturned':
      return { trackingNumber: `VN${evRandomInt(100000000, 999999999)}VN`, reason: evRandomFrom(['Không có người nhận', 'Sai địa chỉ', 'Khách từ chối nhận', 'Hàng hỏng']), attempts: evRandomInt(1, 3) };
    case 'trackShipment':
      return { trackingNumber: `VN${evRandomInt(100000000, 999999999)}VN`, status: evRandomFrom(['Đang vận chuyển', 'Đang giao hàng', 'Đã giao', 'Đang tại bưu cục']), location: evRandomFrom(['Bưu cục Hà Nội', 'Trung tâm phân loại HCM', 'Bưu cục Đà Nẵng']) };
    case 'updateProfile':
      return { field: evRandomFrom(['phone', 'email', 'address', 'name', 'dob']), oldValue: '***', newValue: '***', source: evRandomFrom(['self-service', 'crm-update', 'cms-sync']) };
    default:
      return {};
  }
}

// Generate 300 events
const EV_MANAGEMENT_DATA = [];
for (let i = 1; i <= 300; i++) {
  const type = evRandomFrom(EV_ALL_TYPES);
  const meta = EV_TYPE_META[type];
  const profileId = evRandomFrom(EV_PROFILES);
  const source = evRandomFrom(EV_SOURCES_LIST);
  const scope = evRandomFrom(EV_SCOPES);
  const evDate = evRandomDate(60);
  const targetPool = EV_TARGETS[type] || ['system:internal'];
  const sessionNum = evRandomInt(1000000000000, 9999999999999);

  EV_MANAGEMENT_DATA.push({
    id: `EVMGR${String(i).padStart(6, '0')}`,
    eventType: type,
    label: meta.label,
    icon: meta.icon,
    color: meta.color,
    badgeClass: meta.badgeClass,
    profileId: profileId,
    sessionId: `session-${type}-${sessionNum}`,
    source: source,
    target: evRandomFrom(targetPool),
    scope: scope,
    time: evDate.toISOString(),
    timeDisplay: evDate.toLocaleString('en-US', { year: 'numeric', month: 'numeric', day: 'numeric', hour: 'numeric', minute: '2-digit', second: '2-digit' }),
    payload: generateEvPayload(type),
  });
}

// Sort by time descending
EV_MANAGEMENT_DATA.sort((a, b) => new Date(b.time) - new Date(a.time));

// ============================================================
// EXPORT
// ============================================================
if (typeof window !== 'undefined') {
  window.RULES_DATA = RULES_DATA;
  window.EV_MANAGEMENT_DATA = EV_MANAGEMENT_DATA;
  window.EV_TYPE_META = EV_TYPE_META;
  window.EV_ALL_TYPES = EV_ALL_TYPES;
}
