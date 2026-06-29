// ============================================================
// CDP MOCK DATA - VNPost Customer Data Platform
// ============================================================

const PROVINCES = [
  'Hà Nội', 'TP. Hồ Chí Minh', 'Đà Nẵng', 'Hải Phòng', 'Cần Thơ',
  'Bình Dương', 'Đồng Nai', 'Bà Rịa-Vũng Tàu', 'Quảng Ninh', 'Khánh Hòa',
  'Nghệ An', 'Thanh Hóa', 'Thừa Thiên Huế', 'Lâm Đồng', 'Bình Định',
  'Hà Tĩnh', 'Quảng Bình', 'Nam Định', 'Thái Bình', 'Hà Nam'
];

const LAST_NAMES = ['Nguyễn', 'Trần', 'Lê', 'Phạm', 'Hoàng', 'Huỳnh', 'Phan', 'Vũ', 'Võ', 'Đặng', 'Bùi', 'Đỗ', 'Hồ', 'Ngô', 'Dương'];
const MIDDLE_NAMES = ['Văn', 'Thị', 'Đức', 'Minh', 'Quang', 'Thành', 'Hữu', 'Công', 'Ngọc', 'Thế'];
const FIRST_NAMES_MALE = ['An', 'Bình', 'Cường', 'Dũng', 'Hùng', 'Khoa', 'Long', 'Nam', 'Phong', 'Quân', 'Sơn', 'Thắng', 'Tuấn', 'Vinh', 'Hiếu'];
const FIRST_NAMES_FEMALE = ['Anh', 'Chi', 'Hà', 'Hương', 'Lan', 'Linh', 'Mai', 'Ngân', 'Nhung', 'Phương', 'Thảo', 'Thu', 'Thúy', 'Trang', 'Uyên'];
const DATA_SOURCES = ['CRM', 'CMS', 'Portal', 'MyVNPost'];
const CUSTOMER_TYPES_IND = ['Cá nhân', 'VIP', 'Thường xuyên'];
const BUSINESS_NAMES = [
  'Công ty TNHH Thương mại ABC', 'Tập đoàn XYZ Việt Nam', 'Công ty CP Logistics DEF',
  'Doanh nghiệp tư nhân GHI', 'Công ty TNHH Sản xuất JKL', 'Công ty CP Công nghệ MNO',
  'Tập đoàn PQR Holdings', 'Công ty TNHH STU Trading', 'Công ty CP VWX Group',
  'Doanh nghiệp YZA Manufacturing', 'Công ty TNHH BCD Import Export',
  'Công ty CP EFG Solutions', 'Tập đoàn HIJ Retail', 'Công ty TNHH KLM Logistics',
  'Công ty CP NOP Distribution', 'Công ty TNHH QRS Tech', 'Công ty CP TUV Services',
  'Doanh nghiệp WXY Trading Co.', 'Công ty TNHH ZAB Pharma', 'Công ty CP CDE Energy'
];

function randomInt(min, max) {
  return Math.floor(Math.random() * (max - min + 1)) + min;
}

function randomFrom(arr) {
  return arr[randomInt(0, arr.length - 1)];
}

function randomPhone() {
  const prefixes = ['090', '091', '093', '094', '095', '096', '097', '098', '032', '033', '034', '035', '036', '037', '038', '039', '056', '058', '059', '070', '076', '077', '078', '079'];
  return randomFrom(prefixes) + randomInt(1000000, 9999999).toString();
}

function randomEmail(name, idx) {
  const domains = ['gmail.com', 'yahoo.com', 'hotmail.com', 'vnpost.vn', 'outlook.com'];
  const cleanName = name.toLowerCase().replace(/\s+/g, '.').normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/đ/g, 'd');
  return `${cleanName}${idx}@${randomFrom(domains)}`;
}

function randomDate(daysAgo = 365) {
  const d = new Date();
  d.setDate(d.getDate() - randomInt(0, daysAgo));
  return d;
}

function formatDate(d) {
  return d.toISOString().split('T')[0];
}

function padId(n, prefix = 'VNPOST', len = 6) {
  return prefix + String(n).padStart(len, '0');
}

// ============================================================
// GENERATE INDIVIDUAL CUSTOMERS (100)
// ============================================================
const individualCustomers = [];
for (let i = 1; i <= 100; i++) {
  const gender = Math.random() > 0.5 ? 'male' : 'female';
  const lastName = randomFrom(LAST_NAMES);
  const middleName = randomFrom(MIDDLE_NAMES);
  const firstName = gender === 'male' ? randomFrom(FIRST_NAMES_MALE) : randomFrom(FIRST_NAMES_FEMALE);
  const fullName = `${lastName} ${middleName} ${firstName}`;
  const province = randomFrom(PROVINCES);
  const phone = randomPhone();
  const email = randomEmail(fullName, i);
  const sources = DATA_SOURCES.slice(0, randomInt(1, 4));
  const customerType = randomFrom(CUSTOMER_TYPES_IND);
  const createdAt = randomDate(730);
  const lastActivity = randomDate(30);

  individualCustomers.push({
    id: padId(i),
    cdpId: padId(i),
    name: fullName,
    phone: phone,
    email: email,
    address: `${randomInt(1, 500)} Đường ${randomFrom(['Nguyễn Huệ', 'Lê Lợi', 'Trần Phú', 'Nguyễn Trãi', 'Đinh Tiên Hoàng', 'Phạm Văn Đồng', 'Lê Văn Lương', 'Hoàng Diệu', 'Trường Chinh', 'Nguyễn Văn Cừ'])}, ${province}`,
    province: province,
    type: 'individual',
    customerType: customerType,
    gender: gender,
    dob: `${randomInt(1970, 2000)}-${String(randomInt(1, 12)).padStart(2, '0')}-${String(randomInt(1, 28)).padStart(2, '0')}`,
    sources: sources,
    createdAt: formatDate(createdAt),
    lastActivity: formatDate(lastActivity),
    totalOrders: randomInt(1, 80),
    totalSpent: randomInt(500000, 50000000),
    identityMapping: {
      CRM: sources.includes('CRM') ? `KH${String(randomInt(1, 9999)).padStart(4, '0')}` : null,
      CMS: sources.includes('CMS') ? `CMS${randomInt(100, 999)}` : null,
      Portal: sources.includes('Portal') ? `PT${randomInt(100, 999)}` : null,
      MyVNPost: sources.includes('MyVNPost') ? `APP${randomInt(100, 999)}` : null,
    },
    segments: [],
    avatar: `https://ui-avatars.com/api/?name=${encodeURIComponent(fullName)}&background=random&color=fff&size=64`,
  });
}

// ============================================================
// GENERATE BUSINESS CUSTOMERS (20)
// ============================================================
const businessCustomers = [];
for (let i = 0; i < 20; i++) {
  const idx = 101 + i;
  const companyName = BUSINESS_NAMES[i];
  const contactName = `${randomFrom(LAST_NAMES)} ${randomFrom(MIDDLE_NAMES)} ${randomFrom(FIRST_NAMES_MALE)}`;
  const province = randomFrom(PROVINCES);
  const phone = randomPhone();
  const email = randomEmail(companyName, idx);
  const sources = DATA_SOURCES.slice(0, randomInt(2, 4));
  const createdAt = randomDate(730);
  const lastActivity = randomDate(30);

  businessCustomers.push({
    id: padId(idx),
    cdpId: padId(idx),
    name: companyName,
    contactName: contactName,
    phone: phone,
    email: email,
    address: `Tầng ${randomInt(1, 20)}, Tòa nhà ${randomFrom(['Vietcombank Tower', 'Bitexco', 'Lotte Center', 'Landmark 81', 'Keangnam Hanoi'])} , ${province}`,
    province: province,
    type: 'business',
    customerType: 'Doanh nghiệp',
    taxCode: `${randomInt(1000000000, 9999999999)}`,
    sources: sources,
    createdAt: formatDate(createdAt),
    lastActivity: formatDate(lastActivity),
    totalOrders: randomInt(50, 500),
    totalSpent: randomInt(50000000, 5000000000),
    identityMapping: {
      CRM: sources.includes('CRM') ? `KH${String(randomInt(1, 9999)).padStart(4, '0')}` : null,
      CMS: sources.includes('CMS') ? `CMS${randomInt(100, 999)}` : null,
      Portal: sources.includes('Portal') ? `PT${randomInt(100, 999)}` : null,
      MyVNPost: sources.includes('MyVNPost') ? `APP${randomInt(100, 999)}` : null,
    },
    segments: [],
    avatar: `https://ui-avatars.com/api/?name=${encodeURIComponent(companyName)}&background=1a3a6b&color=fff&size=64`,
  });
}

const ALL_CUSTOMERS = [...individualCustomers, ...businessCustomers];

// ============================================================
// ATTRIBUTE CHANGE HISTORY GENERATOR
// ============================================================

const ATTR_DEFS = [
  {
    key: 'email',
    label: 'Email',
    icon: 'fa-envelope',
    color: '#3b82f6',
    mutate: (val, idx) => {
      // Swap domain or add prefix
      const parts = val.split('@');
      const domains = ['gmail.com', 'yahoo.com', 'hotmail.com', 'outlook.com', 'vnpost.vn'];
      const altDomain = domains.find(d => d !== parts[1]) || 'mail.com';
      return idx % 2 === 0
        ? `${parts[0]}.old@${altDomain}`
        : `old.${parts[0]}@${parts[1]}`;
    },
  },
  {
    key: 'phone',
    label: 'Số điện thoại',
    icon: 'fa-phone',
    color: '#8b5cf6',
    mutate: (val) => {
      // Change last 3 digits
      const suffix = String(randomInt(100, 999));
      return val.slice(0, -3) + suffix;
    },
  },
  {
    key: 'address',
    label: 'Địa chỉ',
    icon: 'fa-map-marker-alt',
    color: '#f59e0b',
    mutate: (val, idx) => {
      const oldStreets = [
        'Lý Thường Kiệt', 'Nguyễn Du', 'Hai Bà Trưng', 'Đinh Tiên Hoàng',
        'Lê Duẩn', 'Trần Quốc Toản', 'Bà Huyện Thanh Quan', 'Võ Thị Sáu',
      ];
      const n = randomInt(1, 300);
      const street = randomFrom(oldStreets);
      // keep same province
      const province = val.split(',').slice(-1)[0].trim();
      return `${n} Đường ${street}, ${province}`;
    },
  },
  {
    key: 'name',
    label: 'Họ tên',
    icon: 'fa-user',
    color: '#10b981',
    mutate: (val) => {
      // Add typo / old spelling (swap middle name)
      const parts = val.split(' ');
      if (parts.length >= 3) {
        const alt = { 'Văn': 'Van', 'Thị': 'Thi', 'Đức': 'Duc', 'Minh': 'Minh', 'Quang': 'Quang', 'Thành': 'Thanh' };
        parts[1] = alt[parts[1]] || parts[1];
      }
      return parts.join(' ');
    },
  },
];

function randomDateBetween(start, end) {
  const s = new Date(start).getTime();
  const e = new Date(end).getTime();
  const t = s + Math.random() * (e - s);
  return new Date(t);
}

function generateAttributeHistory(customer) {
  const numChanges = randomInt(1, 3);
  const history = [];
  const usedAttrs = new Set();

  // Pick unique attributes
  const shuffled = [...ATTR_DEFS].sort(() => Math.random() - 0.5);

  for (let i = 0; i < numChanges; i++) {
    const attrDef = shuffled[i % shuffled.length];
    if (usedAttrs.has(attrDef.key) && numChanges <= ATTR_DEFS.length) continue;
    usedAttrs.add(attrDef.key);

    const currentValue = customer[attrDef.key] || '';
    if (!currentValue) continue;

    // Generate a plausible "old" value
    const oldValue = attrDef.mutate(currentValue, i);

    // Pick a date between customer creation and last activity
    const changeDate = randomDateBetween(customer.createdAt, customer.lastActivity);

    // Pick a source the customer has
    const source = randomFrom(
      customer.sources.length > 0 ? customer.sources : ['CRM']
    );

    history.push({
      date: formatDate(changeDate),
      dateObj: changeDate,
      attribute: attrDef.key,
      attributeLabel: attrDef.label,
      icon: attrDef.icon,
      color: attrDef.color,
      oldValue: oldValue,
      newValue: currentValue,
      source: source,
      changedBy: source === 'CRM' ? 'Đồng bộ CRM' :
                 source === 'CMS' ? 'Cập nhật CMS' :
                 source === 'Portal' ? 'KH tự cập nhật' : 'App MyVNPost',
    });
  }

  // Sort by date descending (newest first)
  history.sort((a, b) => b.dateObj - a.dateObj);
  // Remove internal dateObj helper
  history.forEach(h => delete h.dateObj);

  return history;
}

// Attach attributeHistory to every customer
ALL_CUSTOMERS.forEach(customer => {
  customer.attributeHistory = generateAttributeHistory(customer);
});

// ============================================================
// SEGMENTS
// ============================================================
const SEGMENTS = [
  {
    id: 'SEG001',
    name: 'Khách hàng VIP',
    description: 'Khách hàng có tổng chi tiêu trên 10 triệu đồng hoặc có hơn 20 đơn hàng',
    icon: 'fa-crown',
    color: '#f59e0b',
    badge: 'warning',
    criteria: { minSpent: 10000000, minOrders: 20 },
    members: [],
    createdAt: '2024-01-01',
    updatedAt: formatDate(new Date()),
  },
  {
    id: 'SEG002',
    name: 'Khách hàng Doanh nghiệp',
    description: 'Tất cả khách hàng loại doanh nghiệp (B2B)',
    icon: 'fa-building',
    color: '#3b82f6',
    badge: 'primary',
    criteria: { type: 'business' },
    members: [],
    createdAt: '2024-01-01',
    updatedAt: formatDate(new Date()),
  },
  {
    id: 'SEG003',
    name: 'Khách hàng hoạt động 30 ngày',
    description: 'Khách hàng có hoạt động trong 30 ngày gần nhất',
    icon: 'fa-fire',
    color: '#ef4444',
    badge: 'danger',
    criteria: { activeLastDays: 30 },
    members: [],
    createdAt: '2024-02-01',
    updatedAt: formatDate(new Date()),
  },
  {
    id: 'SEG004',
    name: 'Khách hàng thường xuyên',
    description: 'Khách hàng có trên 10 đơn hàng',
    icon: 'fa-repeat',
    color: '#8b5cf6',
    badge: 'purple',
    criteria: { minOrders: 10 },
    members: [],
    createdAt: '2024-02-15',
    updatedAt: formatDate(new Date()),
  },
  {
    id: 'SEG005',
    name: 'Khách hàng mới',
    description: 'Khách hàng đăng ký trong 90 ngày gần nhất',
    icon: 'fa-user-plus',
    color: '#10b981',
    badge: 'success',
    criteria: { newLastDays: 90 },
    members: [],
    createdAt: '2024-03-01',
    updatedAt: formatDate(new Date()),
  },
];

// Assign customers to segments
const today = new Date();
ALL_CUSTOMERS.forEach(customer => {
  const lastAct = new Date(customer.lastActivity);
  const created = new Date(customer.createdAt);
  const daysSinceActivity = Math.floor((today - lastAct) / (1000 * 60 * 60 * 24));
  const daysSinceCreated = Math.floor((today - created) / (1000 * 60 * 60 * 24));

  // SEG001: VIP
  if (customer.totalSpent >= 10000000 || customer.totalOrders >= 20) {
    SEGMENTS[0].members.push(customer.id);
    customer.segments.push('SEG001');
  }
  // SEG002: Business
  if (customer.type === 'business') {
    SEGMENTS[1].members.push(customer.id);
    customer.segments.push('SEG002');
  }
  // SEG003: Active 30 days
  if (daysSinceActivity <= 30) {
    SEGMENTS[2].members.push(customer.id);
    customer.segments.push('SEG003');
  }
  // SEG004: Frequent (>10 orders)
  if (customer.totalOrders > 10) {
    SEGMENTS[3].members.push(customer.id);
    customer.segments.push('SEG004');
  }
  // SEG005: New (within 90 days)
  if (daysSinceCreated <= 90) {
    SEGMENTS[4].members.push(customer.id);
    customer.segments.push('SEG005');
  }
});

// ============================================================
// GENERATE EVENTS (500)
// ============================================================
const EVENT_TYPES = [
  { type: 'login', label: 'Đăng nhập', icon: 'fa-sign-in-alt', color: '#3b82f6' },
  { type: 'create_order', label: 'Tạo đơn hàng', icon: 'fa-box', color: '#10b981' },
  { type: 'track_shipment', label: 'Theo dõi đơn hàng', icon: 'fa-truck', color: '#f59e0b' },
  { type: 'payment', label: 'Thanh toán', icon: 'fa-credit-card', color: '#8b5cf6' },
  { type: 'register', label: 'Đăng ký tài khoản', icon: 'fa-user-plus', color: '#10b981' },
  { type: 'view_product', label: 'Xem sản phẩm', icon: 'fa-eye', color: '#6b7280' },
  { type: 'update_profile', label: 'Cập nhật hồ sơ', icon: 'fa-user-edit', color: '#06b6d4' },
  { type: 'rate_service', label: 'Đánh giá dịch vụ', icon: 'fa-star', color: '#f59e0b' },
  { type: 'contact_support', label: 'Liên hệ hỗ trợ', icon: 'fa-headset', color: '#ef4444' },
  { type: 'cancel_order', label: 'Hủy đơn hàng', icon: 'fa-times-circle', color: '#ef4444' },
];

const EVENTS = [];
for (let i = 1; i <= 500; i++) {
  const customer = randomFrom(ALL_CUSTOMERS);
  const eventType = randomFrom(EVENT_TYPES);
  const eventDate = randomDate(180);

  EVENTS.push({
    id: `EVT${String(i).padStart(6, '0')}`,
    customerId: customer.id,
    customerName: customer.name,
    type: eventType.type,
    label: eventType.label,
    icon: eventType.icon,
    color: eventType.color,
    source: randomFrom(DATA_SOURCES),
    timestamp: eventDate.toISOString(),
    date: formatDate(eventDate),
    details: generateEventDetails(eventType.type, customer),
  });
}

function generateEventDetails(type, customer) {
  switch (type) {
    case 'create_order':
      return { orderId: `ORD${randomInt(100000, 999999)}`, amount: randomInt(50000, 5000000), service: randomFrom(['Bưu phẩm thường', 'Chuyển phát nhanh', 'Bưu phẩm quốc tế', 'EMS']) };
    case 'payment':
      return { amount: randomInt(50000, 5000000), method: randomFrom(['Tiền mặt', 'Chuyển khoản', 'Ví MoMo', 'ZaloPay', 'Thẻ ATM']) };
    case 'track_shipment':
      return { trackingCode: `VN${randomInt(100000000, 999999999)}VN`, status: randomFrom(['Đang vận chuyển', 'Đã giao', 'Đang chờ lấy']) };
    case 'rate_service':
      return { rating: randomInt(3, 5), comment: randomFrom(['Dịch vụ tốt', 'Nhân viên thân thiện', 'Giao hàng nhanh', 'Đóng gói cẩn thận']) };
    case 'login':
      return { device: randomFrom(['Mobile', 'Desktop', 'Tablet']), ip: `${randomInt(1, 254)}.${randomInt(1, 254)}.${randomInt(1, 254)}.${randomInt(1, 254)}` };
    default:
      return {};
  }
}

// Sort events by timestamp
EVENTS.sort((a, b) => new Date(b.timestamp) - new Date(a.timestamp));

// ============================================================
// DUPLICATE PROFILES (for Identity Resolution)
// ============================================================
const DUPLICATE_PROFILES = [
  {
    id: 'DUP001',
    primaryCustomerId: ALL_CUSTOMERS[14].id,
    secondaryCustomerId: ALL_CUSTOMERS[22].id,
    profile1: {
      cdpId: 'VNPOST000015',
      name: ALL_CUSTOMERS[14].name,
      phone: ALL_CUSTOMERS[14].phone,
      email: ALL_CUSTOMERS[14].email,
      address: ALL_CUSTOMERS[14].address,
      source: 'CRM',
    },
    profile2: {
      cdpId: 'VNPOST000023',
      name: ALL_CUSTOMERS[22].name.replace(/[aeiou]/gi, (m) => m === m.toLowerCase() ? m.toUpperCase() : m.toLowerCase()),
      phone: ALL_CUSTOMERS[14].phone,
      email: `alt_${ALL_CUSTOMERS[22].email}`,
      address: ALL_CUSTOMERS[22].address,
      source: 'Portal',
    },
    matchScore: 94,
    matchReasons: ['Số điện thoại trùng khớp', 'Tên tương đồng (>90%)', 'Địa chỉ tỉnh thành trùng khớp'],
    status: 'pending',
  },
  {
    id: 'DUP002',
    primaryCustomerId: ALL_CUSTOMERS[30].id,
    secondaryCustomerId: ALL_CUSTOMERS[44].id,
    profile1: {
      cdpId: 'VNPOST000031',
      name: ALL_CUSTOMERS[30].name,
      phone: ALL_CUSTOMERS[30].phone,
      email: ALL_CUSTOMERS[30].email,
      address: ALL_CUSTOMERS[30].address,
      source: 'MyVNPost',
    },
    profile2: {
      cdpId: 'VNPOST000045',
      name: ALL_CUSTOMERS[30].name,
      phone: `0${ALL_CUSTOMERS[30].phone.substring(1)}`,
      email: ALL_CUSTOMERS[30].email,
      address: ALL_CUSTOMERS[44].address,
      source: 'CMS',
    },
    matchScore: 98,
    matchReasons: ['Email trùng khớp 100%', 'Tên trùng khớp 100%', 'Số điện thoại tương đồng'],
    status: 'pending',
  },
  {
    id: 'DUP003',
    primaryCustomerId: ALL_CUSTOMERS[51].id,
    secondaryCustomerId: ALL_CUSTOMERS[67].id,
    profile1: {
      cdpId: 'VNPOST000052',
      name: ALL_CUSTOMERS[51].name,
      phone: ALL_CUSTOMERS[51].phone,
      email: ALL_CUSTOMERS[51].email,
      address: ALL_CUSTOMERS[51].address,
      source: 'CRM',
    },
    profile2: {
      cdpId: 'VNPOST000068',
      name: ALL_CUSTOMERS[51].name + ' Jr.',
      phone: ALL_CUSTOMERS[51].phone,
      email: `${ALL_CUSTOMERS[51].email.split('@')[0]}2@${ALL_CUSTOMERS[51].email.split('@')[1]}`,
      address: ALL_CUSTOMERS[67].address,
      source: 'Portal',
    },
    matchScore: 87,
    matchReasons: ['Số điện thoại trùng khớp', 'Tên tương đồng (>85%)'],
    status: 'pending',
  },
  {
    id: 'DUP004',
    primaryCustomerId: ALL_CUSTOMERS[71].id,
    secondaryCustomerId: ALL_CUSTOMERS[88].id,
    profile1: {
      cdpId: 'VNPOST000072',
      name: ALL_CUSTOMERS[71].name,
      phone: ALL_CUSTOMERS[71].phone,
      email: ALL_CUSTOMERS[71].email,
      address: ALL_CUSTOMERS[71].address,
      source: 'Portal',
    },
    profile2: {
      cdpId: 'VNPOST000089',
      name: ALL_CUSTOMERS[71].name,
      phone: ALL_CUSTOMERS[71].phone,
      email: ALL_CUSTOMERS[71].email.toUpperCase(),
      address: ALL_CUSTOMERS[88].address,
      source: 'CRM',
    },
    matchScore: 99,
    matchReasons: ['Email trùng khớp (case-insensitive)', 'Số điện thoại trùng khớp', 'Tên trùng khớp 100%'],
    status: 'pending',
  },
  {
    id: 'DUP005',
    primaryCustomerId: ALL_CUSTOMERS[82].id,
    secondaryCustomerId: ALL_CUSTOMERS[96].id,
    profile1: {
      cdpId: 'VNPOST000083',
      name: ALL_CUSTOMERS[82].name,
      phone: ALL_CUSTOMERS[82].phone,
      email: ALL_CUSTOMERS[82].email,
      address: ALL_CUSTOMERS[82].address,
      source: 'MyVNPost',
    },
    profile2: {
      cdpId: 'VNPOST000097',
      name: ALL_CUSTOMERS[82].name,
      phone: ALL_CUSTOMERS[82].phone,
      email: ALL_CUSTOMERS[96].email,
      address: ALL_CUSTOMERS[96].address,
      source: 'CMS',
    },
    matchScore: 91,
    matchReasons: ['Số điện thoại trùng khớp', 'Tên trùng khớp 100%', 'Địa chỉ tương đồng'],
    status: 'pending',
  },
  {
    id: 'DUP006',
    primaryCustomerId: ALL_CUSTOMERS[5].id,
    secondaryCustomerId: ALL_CUSTOMERS[18].id,
    profile1: {
      cdpId: ALL_CUSTOMERS[5].cdpId,
      name: ALL_CUSTOMERS[5].name,
      phone: ALL_CUSTOMERS[5].phone,
      email: ALL_CUSTOMERS[5].email,
      address: ALL_CUSTOMERS[5].address,
      source: 'CRM',
    },
    profile2: {
      cdpId: ALL_CUSTOMERS[18].cdpId,
      name: ALL_CUSTOMERS[5].name.split(' ').map((w,i) => i === 1 ? w.replace('Văn','Van').replace('Thị','Thi') : w).join(' '),
      phone: ALL_CUSTOMERS[5].phone,
      email: ALL_CUSTOMERS[18].email,
      address: ALL_CUSTOMERS[18].address,
      source: 'Portal',
    },
    matchScore: 93,
    matchReasons: ['Số điện thoại trùng khớp', 'Tên tương đồng (>90%)', 'Địa chỉ tỉnh thành trùng khớp'],
    status: 'pending',
  },
  {
    id: 'DUP007',
    primaryCustomerId: ALL_CUSTOMERS[33].id,
    secondaryCustomerId: ALL_CUSTOMERS[57].id,
    profile1: {
      cdpId: ALL_CUSTOMERS[33].cdpId,
      name: ALL_CUSTOMERS[33].name,
      phone: ALL_CUSTOMERS[33].phone,
      email: ALL_CUSTOMERS[33].email,
      address: ALL_CUSTOMERS[33].address,
      source: 'CMS',
    },
    profile2: {
      cdpId: ALL_CUSTOMERS[57].cdpId,
      name: ALL_CUSTOMERS[33].name,
      phone: ALL_CUSTOMERS[33].phone,
      email: `new.${ALL_CUSTOMERS[33].email}`,
      address: ALL_CUSTOMERS[57].address,
      source: 'MyVNPost',
    },
    matchScore: 96,
    matchReasons: ['Số điện thoại trùng khớp', 'Tên trùng khớp 100%', 'Email tương tự'],
    status: 'pending',
  },
  {
    id: 'DUP008',
    primaryCustomerId: ALL_CUSTOMERS[40].id,
    secondaryCustomerId: ALL_CUSTOMERS[63].id,
    profile1: {
      cdpId: ALL_CUSTOMERS[40].cdpId,
      name: ALL_CUSTOMERS[40].name,
      phone: ALL_CUSTOMERS[40].phone,
      email: ALL_CUSTOMERS[40].email,
      address: ALL_CUSTOMERS[40].address,
      source: 'CRM',
    },
    profile2: {
      cdpId: ALL_CUSTOMERS[63].cdpId,
      name: ALL_CUSTOMERS[40].name + ' (cũ)',
      phone: ALL_CUSTOMERS[40].phone,
      email: ALL_CUSTOMERS[40].email.replace('@', '.cu@'),
      address: ALL_CUSTOMERS[63].address,
      source: 'CRM',
    },
    matchScore: 89,
    matchReasons: ['Số điện thoại trùng khớp', 'Tên tương đồng (>85%)', 'Email tương tự'],
    status: 'pending',
  },
  {
    id: 'DUP009',
    primaryCustomerId: ALL_CUSTOMERS[10].id,
    secondaryCustomerId: ALL_CUSTOMERS[75].id,
    profile1: {
      cdpId: ALL_CUSTOMERS[10].cdpId,
      name: ALL_CUSTOMERS[10].name,
      phone: ALL_CUSTOMERS[10].phone,
      email: ALL_CUSTOMERS[10].email,
      address: ALL_CUSTOMERS[10].address,
      source: 'Portal',
    },
    profile2: {
      cdpId: ALL_CUSTOMERS[75].cdpId,
      name: ALL_CUSTOMERS[10].name,
      phone: ALL_CUSTOMERS[10].phone,
      email: ALL_CUSTOMERS[75].email,
      address: ALL_CUSTOMERS[75].address,
      source: 'CRM',
    },
    matchScore: 95,
    matchReasons: ['Số điện thoại trùng khớp', 'Tên trùng khớp 100%', 'Địa chỉ tỉnh thành trùng khớp'],
    status: 'pending',
  },
  {
    id: 'DUP010',
    primaryCustomerId: ALL_CUSTOMERS[20].id,
    secondaryCustomerId: ALL_CUSTOMERS[49].id,
    profile1: {
      cdpId: ALL_CUSTOMERS[20].cdpId,
      name: ALL_CUSTOMERS[20].name,
      phone: ALL_CUSTOMERS[20].phone,
      email: ALL_CUSTOMERS[20].email,
      address: ALL_CUSTOMERS[20].address,
      source: 'MyVNPost',
    },
    profile2: {
      cdpId: ALL_CUSTOMERS[49].cdpId,
      name: ALL_CUSTOMERS[20].name,
      phone: ALL_CUSTOMERS[20].phone,
      email: ALL_CUSTOMERS[49].email,
      address: ALL_CUSTOMERS[49].address,
      source: 'Portal',
    },
    matchScore: 92,
    matchReasons: ['Số điện thoại trùng khớp', 'Tên trùng khớp 100%'],
    status: 'pending',
  },
  {
    id: 'DUP011',
    primaryCustomerId: ALL_CUSTOMERS[100].id,
    secondaryCustomerId: ALL_CUSTOMERS[101].id,
    profile1: {
      cdpId: ALL_CUSTOMERS[100].cdpId,
      name: ALL_CUSTOMERS[100].name,
      phone: ALL_CUSTOMERS[100].phone,
      email: ALL_CUSTOMERS[100].email,
      address: ALL_CUSTOMERS[100].address,
      source: 'CRM',
    },
    profile2: {
      cdpId: ALL_CUSTOMERS[101].cdpId,
      name: ALL_CUSTOMERS[100].name.replace('Công ty TNHH', 'CTY TNHH').replace('Tập đoàn', 'Tap Doan'),
      phone: ALL_CUSTOMERS[100].phone,
      email: ALL_CUSTOMERS[101].email,
      address: ALL_CUSTOMERS[101].address,
      source: 'Portal',
    },
    matchScore: 97,
    matchReasons: ['Số điện thoại trùng khớp', 'Tên doanh nghiệp tương đồng (>95%)', 'Mã số thuế trùng khớp'],
    status: 'pending',
  },
  {
    id: 'DUP012',
    primaryCustomerId: ALL_CUSTOMERS[102].id,
    secondaryCustomerId: ALL_CUSTOMERS[103].id,
    profile1: {
      cdpId: ALL_CUSTOMERS[102].cdpId,
      name: ALL_CUSTOMERS[102].name,
      phone: ALL_CUSTOMERS[102].phone,
      email: ALL_CUSTOMERS[102].email,
      address: ALL_CUSTOMERS[102].address,
      source: 'CMS',
    },
    profile2: {
      cdpId: ALL_CUSTOMERS[103].cdpId,
      name: ALL_CUSTOMERS[102].name.replace('Công ty ', 'Cty ').replace('Tập đoàn ', 'T.Đ '),
      phone: ALL_CUSTOMERS[102].phone,
      email: ALL_CUSTOMERS[103].email,
      address: ALL_CUSTOMERS[103].address,
      source: 'CRM',
    },
    matchScore: 98,
    matchReasons: ['Số điện thoại trùng khớp', 'Tên doanh nghiệp tương đồng (>95%)', 'Địa chỉ tỉnh thành trùng khớp'],
    status: 'pending',
  },
  {
    id: 'DUP013',
    primaryCustomerId: ALL_CUSTOMERS[55].id,
    secondaryCustomerId: ALL_CUSTOMERS[78].id,
    profile1: {
      cdpId: ALL_CUSTOMERS[55].cdpId,
      name: ALL_CUSTOMERS[55].name,
      phone: ALL_CUSTOMERS[55].phone,
      email: ALL_CUSTOMERS[55].email,
      address: ALL_CUSTOMERS[55].address,
      source: 'CRM',
    },
    profile2: {
      cdpId: ALL_CUSTOMERS[78].cdpId,
      name: ALL_CUSTOMERS[55].name,
      phone: ALL_CUSTOMERS[55].phone,
      email: ALL_CUSTOMERS[78].email,
      address: ALL_CUSTOMERS[78].address,
      source: 'CMS',
    },
    matchScore: 88,
    matchReasons: ['Số điện thoại trùng khớp', 'Tên trùng khớp 100%'],
    status: 'pending',
  },
  {
    id: 'DUP014',
    primaryCustomerId: ALL_CUSTOMERS[25].id,
    secondaryCustomerId: ALL_CUSTOMERS[37].id,
    profile1: {
      cdpId: ALL_CUSTOMERS[25].cdpId,
      name: ALL_CUSTOMERS[25].name,
      phone: ALL_CUSTOMERS[25].phone,
      email: ALL_CUSTOMERS[25].email,
      address: ALL_CUSTOMERS[25].address,
      source: 'Portal',
    },
    profile2: {
      cdpId: ALL_CUSTOMERS[37].cdpId,
      name: ALL_CUSTOMERS[25].name,
      phone: ALL_CUSTOMERS[25].phone,
      email: ALL_CUSTOMERS[25].email,
      address: ALL_CUSTOMERS[37].address,
      source: 'MyVNPost',
    },
    matchScore: 100,
    matchReasons: ['Email trùng khớp 100%', 'Số điện thoại trùng khớp', 'Tên trùng khớp 100%'],
    status: 'pending',
  },
  {
    id: 'DUP015',
    primaryCustomerId: ALL_CUSTOMERS[60].id,
    secondaryCustomerId: ALL_CUSTOMERS[85].id,
    profile1: {
      cdpId: ALL_CUSTOMERS[60].cdpId,
      name: ALL_CUSTOMERS[60].name,
      phone: ALL_CUSTOMERS[60].phone,
      email: ALL_CUSTOMERS[60].email,
      address: ALL_CUSTOMERS[60].address,
      source: 'CRM',
    },
    profile2: {
      cdpId: ALL_CUSTOMERS[85].cdpId,
      name: ALL_CUSTOMERS[60].name.split(' ').reverse().join(' '),
      phone: ALL_CUSTOMERS[60].phone,
      email: `${ALL_CUSTOMERS[60].email.split('@')[0]}.backup@${ALL_CUSTOMERS[60].email.split('@')[1]}`,
      address: ALL_CUSTOMERS[85].address,
      source: 'Portal',
    },
    matchScore: 86,
    matchReasons: ['Số điện thoại trùng khớp', 'Tên tương đồng (>80%)', 'Email domain trùng khớp'],
    status: 'pending',
  },
];

// Build quick-lookup map: customerId -> dupId
const CUSTOMER_DUP_MAP = {};
DUPLICATE_PROFILES.forEach(dup => {
  if (dup.primaryCustomerId) CUSTOMER_DUP_MAP[dup.primaryCustomerId] = dup.id;
  if (dup.secondaryCustomerId) CUSTOMER_DUP_MAP[dup.secondaryCustomerId] = dup.id;
});

// Attach duplicateStatus to each customer
ALL_CUSTOMERS.forEach(c => {
  const dupId = CUSTOMER_DUP_MAP[c.id];
  if (dupId) {
    c.duplicatePairId = dupId;
    c.duplicateStatus = 'suspect'; // will be updated to 'merged' on merge
  } else {
    c.duplicateStatus = 'normal';
  }
});

// ============================================================
// PROVINCE STATS
// ============================================================
const PROVINCE_STATS = {};
ALL_CUSTOMERS.forEach(c => {
  PROVINCE_STATS[c.province] = (PROVINCE_STATS[c.province] || 0) + 1;
});

// ============================================================
// EXPORT
// ============================================================
const CDP_DATA = {
  customers: ALL_CUSTOMERS,
  individualCustomers,
  businessCustomers,
  events: EVENTS,
  segments: SEGMENTS,
  duplicateProfiles: DUPLICATE_PROFILES,
  customerDupMap: CUSTOMER_DUP_MAP,
  provinceStats: PROVINCE_STATS,
  stats: {
    totalCustomers: ALL_CUSTOMERS.length,
    totalMerged: 3,
    totalSuspect: DUPLICATE_PROFILES.length * 2,  // 2 customers per pair
    totalEvents: EVENTS.length,
    totalSegments: SEGMENTS.length,
  }
};
