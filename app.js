/* ─────────────────────────────────────────────────
   NatePad Web — Application Logic
   ───────────────────────────────────────────────── */

// ── Theme Catalog ──────────────────────────────────
// Ported 1:1 from Theme.swift — 31 themes total.
const THEMES = [
  { id:'natepad',    name:'NatePad',    bg:[224,25,8],   fg:[210,40,98],  pri:[245,85,66], acc:[200,90,60], bgL:0.08 },
  { id:'light',      name:'Light',      bg:[0,0,100],    fg:[215,28,17],  pri:[259,94,51], acc:[314,100,47],bgL:1.00 },
  { id:'dark',       name:'Dark',       bg:[0,0,11],     fg:[0,0,90],     pri:[259,94,70], acc:[314,100,70],bgL:0.11 },
  { id:'forest',     name:'Forest',     bg:[0,12,8],     fg:[0,12,82],    pri:[141,72,42], acc:[141,75,48], bgL:0.08 },
  { id:'garden',     name:'Garden',     bg:[0,4,91],     fg:[0,3,6],      pri:[139,16,43], acc:[97,37,93],  bgL:0.91 },
  { id:'emerald',    name:'Emerald',    bg:[0,0,100],    fg:[219,20,25],  pri:[141,50,60], acc:[219,96,60], bgL:1.00 },
  { id:'aqua',       name:'Aqua',       bg:[219,53,43],  fg:[218,100,89], pri:[182,93,49], acc:[274,31,57], bgL:0.43 },
  { id:'ocean',      name:'Ocean',      bg:[207,50,14],  fg:[207,30,90],  pri:[199,89,64], acc:[259,50,67], bgL:0.14 },
  { id:'night',      name:'Night',      bg:[222,47,11],  fg:[222,65,82],  pri:[198,93,60], acc:[234,89,74], bgL:0.11 },
  { id:'dracula',    name:'Dracula',    bg:[231,15,18],  fg:[60,30,96],   pri:[326,100,74],acc:[265,89,78], bgL:0.18 },
  { id:'synthwave',  name:'Synthwave',  bg:[254,59,26],  fg:[260,60,98],  pri:[321,70,69], acc:[197,87,65], bgL:0.26 },
  { id:'halloween',  name:'Halloween',  bg:[0,0,13],     fg:[0,0,83],     pri:[32,89,52],  acc:[271,46,42], bgL:0.13 },
  { id:'coffee',     name:'Coffee',     bg:[306,19,11],  fg:[37,30,70],   pri:[30,67,58],  acc:[182,25,50], bgL:0.11 },
  { id:'business',   name:'Business',   bg:[0,0,13],     fg:[0,0,82],     pri:[210,64,55], acc:[200,13,65], bgL:0.13 },
  { id:'luxury',     name:'Luxury',     bg:[240,10,4],   fg:[37,67,58],   pri:[0,0,100],   acc:[218,54,50], bgL:0.04 },
  { id:'black',      name:'Black',      bg:[0,0,0],      fg:[0,0,80],     pri:[0,0,70],    acc:[0,0,50],    bgL:0.00 },
  { id:'cupcake',    name:'Cupcake',    bg:[24,33,97],   fg:[280,46,14],  pri:[183,47,59], acc:[338,71,78], bgL:0.97 },
  { id:'valentine',  name:'Valentine',  bg:[318,46,89],  fg:[344,38,28],  pri:[353,74,67], acc:[254,86,77], bgL:0.89 },
  { id:'pastel',     name:'Pastel',     bg:[0,0,100],    fg:[0,0,20],     pri:[284,22,70], acc:[352,70,80], bgL:1.00 },
  { id:'fantasy',    name:'Fantasy',    bg:[0,0,100],    fg:[215,28,17],  pri:[296,83,35], acc:[200,100,37],bgL:1.00 },
  { id:'retro',      name:'Retro',      bg:[45,47,80],   fg:[345,5,15],   pri:[3,60,55],   acc:[145,35,50], bgL:0.80 },
  { id:'bumblebee',  name:'Bumblebee',  bg:[0,0,100],    fg:[0,0,20],     pri:[41,74,53],  acc:[50,94,58],  bgL:1.00 },
  { id:'lemonade',   name:'Lemonade',   bg:[0,0,100],    fg:[0,0,20],     pri:[89,96,31],  acc:[60,81,45],  bgL:1.00 },
  { id:'corporate',  name:'Corporate',  bg:[0,0,100],    fg:[233,27,13],  pri:[229,96,64], acc:[215,26,59], bgL:1.00 },
  { id:'cmyk',       name:'CMYK',       bg:[0,0,100],    fg:[0,0,20],     pri:[203,83,60], acc:[335,78,60], bgL:1.00 },
  { id:'autumn',     name:'Autumn',     bg:[0,0,95],     fg:[0,0,19],     pri:[344,96,38], acc:[0,63,50],   bgL:0.95 },
  { id:'winter',     name:'Winter',     bg:[0,0,100],    fg:[214,30,32],  pri:[212,100,51],acc:[247,47,43], bgL:1.00 },
  { id:'acid',       name:'Acid',       bg:[0,0,98],     fg:[0,0,20],     pri:[303,90,45], acc:[27,100,50], bgL:0.98 },
  { id:'cyberpunk',  name:'Cyberpunk',  bg:[56,100,50],  fg:[56,100,10],  pri:[345,100,50],acc:[195,80,55], bgL:0.50 },
  { id:'wireframe',  name:'Wireframe',  bg:[0,0,100],    fg:[0,0,20],     pri:[0,0,40],    acc:[0,0,60],    bgL:1.00 },
  { id:'lofi',       name:'Lofi',       bg:[0,0,100],    fg:[0,0,0],      pri:[0,0,5],     acc:[0,2,30],    bgL:1.00 },
];

// ── State ──────────────────────────────────────────
let currentTheme = localStorage.getItem('natepad.theme') || 'natepad';
let keys = JSON.parse(localStorage.getItem('natepad.keys') || '[]');
let encryptRecipientIds = [];

// ── DOM Helpers ────────────────────────────────────
const $ = (sel) => document.querySelector(sel);
const $$ = (sel) => document.querySelectorAll(sel);

// ── Theme Engine ───────────────────────────────────
function applyTheme(id) {
  const t = THEMES.find(t => t.id === id) || THEMES[0];
  const r = document.documentElement.style;
  r.setProperty('--bg-h', t.bg[0]);
  r.setProperty('--bg-s', t.bg[1] + '%');
  r.setProperty('--bg-l', t.bg[2] + '%');
  r.setProperty('--fg-h', t.fg[0]);
  r.setProperty('--fg-s', t.fg[1] + '%');
  r.setProperty('--fg-l', t.fg[2] + '%');
  r.setProperty('--pri-h', t.pri[0]);
  r.setProperty('--pri-s', t.pri[1] + '%');
  r.setProperty('--pri-l', t.pri[2] + '%');
  r.setProperty('--acc-h', t.acc[0]);
  r.setProperty('--acc-s', t.acc[1] + '%');
  r.setProperty('--acc-l', t.acc[2] + '%');
  r.setProperty('--bg-lightness', t.bgL);

  // Flip color-scheme for light themes
  document.documentElement.style.colorScheme = t.bgL > 0.5 ? 'light' : 'dark';

  // Update meta theme-color
  const meta = document.querySelector('meta[name="theme-color"]');
  if (meta) meta.content = `hsl(${t.bg[0]}, ${t.bg[1]}%, ${t.bg[2]}%)`;

  currentTheme = id;
  localStorage.setItem('natepad.theme', id);
  renderThemeGrid();
}

function renderThemeGrid() {
  const grid = $('#theme-grid');
  grid.innerHTML = '';
  for (const t of THEMES) {
    const btn = document.createElement('button');
    btn.className = 'theme-swatch' + (t.id === currentTheme ? ' active' : '');
    btn.innerHTML = `
      <div class="swatch-circle" style="background:hsl(${t.bg[0]},${t.bg[1]}%,${t.bg[2]}%)">
        <div class="swatch-dot-pri" style="background:hsl(${t.pri[0]},${t.pri[1]}%,${t.pri[2]}%)"></div>
        <div class="swatch-dot-acc" style="background:hsl(${t.acc[0]},${t.acc[1]}%,${t.acc[2]}%)"></div>
      </div>
      <span class="swatch-label">${t.name}</span>
    `;
    btn.addEventListener('click', () => applyTheme(t.id));
    grid.appendChild(btn);
  }
}

// ── Tab Navigation ─────────────────────────────────
function initTabs() {
  const btns = $$('.tab-btn');
  const panes = $$('.tab-pane');
  btns.forEach(btn => {
    btn.addEventListener('click', () => {
      btns.forEach(b => { b.classList.remove('active'); b.setAttribute('aria-selected', 'false'); });
      panes.forEach(p => p.classList.remove('active'));
      btn.classList.add('active');
      btn.setAttribute('aria-selected', 'true');
      $(`#pane-${btn.dataset.tab}`).classList.add('active');
    });
  });
}

// ── Key Store ──────────────────────────────────────
function saveKeys() {
  localStorage.setItem('natepad.keys', JSON.stringify(keys));
  renderKeys();
  refreshKeySelectors();
  updateNoKeysWarning();
}

function updateNoKeysWarning() {
  const banner = $('#no-keys-warning');
  banner.style.display = keys.length === 0 ? 'flex' : 'none';
}

function renderKeys() {
  const list = $('#key-list');
  const empty = $('#keys-empty');
  if (keys.length === 0) {
    list.innerHTML = '';
    empty.style.display = 'block';
    return;
  }
  empty.style.display = 'none';
  list.innerHTML = keys.map((k, i) => `
    <div class="glass-card key-card">
      <div class="key-avatar">🔑</div>
      <div class="key-info">
        <div class="key-label">${esc(k.label)}</div>
        <div class="key-fp">${esc(k.fingerprint || '—')}</div>
      </div>
      <span class="key-type">${k.hasPrivate ? 'Pair' : 'Pub'}</span>
      <div class="key-actions">
        <button class="key-action-btn" title="Export" onclick="exportKey(${i})">📤</button>
        <button class="key-action-btn danger" title="Delete" onclick="deleteKey(${i})">🗑️</button>
      </div>
    </div>
  `).join('');
}

function refreshKeySelectors() {
  // Encrypt recipients
  const er = $('#encrypt-recipients');
  er.innerHTML = '<option value="">Select a public key…</option>';
  keys.forEach((k, i) => {
    er.innerHTML += `<option value="${i}">${esc(k.label)}</option>`;
  });

  // Decrypt key
  const dk = $('#decrypt-key');
  dk.innerHTML = '<option value="">Auto-detect</option>';
  keys.filter(k => k.hasPrivate).forEach((k, i) => {
    const idx = keys.indexOf(k);
    dk.innerHTML += `<option value="${idx}">${esc(k.label)}</option>`;
  });

  // Sign key
  const sk = $('#sign-key');
  sk.innerHTML = '<option value="">Select a private key…</option>';
  keys.filter(k => k.hasPrivate).forEach((k) => {
    const idx = keys.indexOf(k);
    sk.innerHTML += `<option value="${idx}">${esc(k.label)}</option>`;
  });
}

function esc(str) {
  const div = document.createElement('div');
  div.textContent = str;
  return div.innerHTML;
}

// ── Toast ──────────────────────────────────────────
function showToast(msg) {
  const container = $('#toast-container');
  const t = document.createElement('div');
  t.className = 'toast';
  t.textContent = msg;
  container.appendChild(t);
  setTimeout(() => t.remove(), 3000);
}

// ── Dialog Helpers ─────────────────────────────────
function openDialog(id) {
  const dlg = $(`#${id}`);
  dlg.showModal();
}

function closeDialog(id) {
  const dlg = $(`#${id}`);
  dlg.close();
}

function initDialogs() {
  // Close buttons
  $$('[data-close]').forEach(btn => {
    btn.addEventListener('click', () => {
      btn.closest('dialog').close();
    });
  });

  // Light-dismiss (click backdrop)
  $$('dialog').forEach(dlg => {
    dlg.addEventListener('click', (e) => {
      if (e.target === dlg) dlg.close();
    });
  });

  // Dashboard action cards
  $$('.action-card').forEach(card => {
    card.addEventListener('click', () => {
      const action = card.dataset.action;
      openDialog(`dlg-${action}`);
    });
    card.addEventListener('keydown', (e) => {
      if (e.key === 'Enter' || e.key === ' ') {
        e.preventDefault();
        card.click();
      }
    });
  });
}

// ── Char Counters ──────────────────────────────────
function initCharCounters() {
  const pairs = [
    ['encrypt-message', 'encrypt-count'],
    ['decrypt-message', 'decrypt-count'],
    ['sign-message', 'sign-count'],
    ['verify-message', 'verify-count'],
  ];
  pairs.forEach(([textId, countId]) => {
    const ta = $(`#${textId}`);
    const ct = $(`#${countId}`);
    ta.addEventListener('input', () => {
      const len = ta.value.length;
      ct.textContent = `${len.toLocaleString()} char${len !== 1 ? 's' : ''}`;
    });
  });
}

// ── Encrypt Recipients ─────────────────────────────
function initEncryptRecipients() {
  const sel = $('#encrypt-recipients');
  const chips = $('#encrypt-chips');

  sel.addEventListener('change', () => {
    const idx = parseInt(sel.value);
    if (isNaN(idx) || encryptRecipientIds.includes(idx)) { sel.value = ''; return; }
    encryptRecipientIds.push(idx);
    sel.value = '';
    renderEncryptChips();
  });
}

function renderEncryptChips() {
  const chips = $('#encrypt-chips');
  chips.innerHTML = encryptRecipientIds.map(idx => {
    const k = keys[idx];
    return `<span class="chip">${esc(k.label)} <button onclick="removeRecipient(${idx})">&times;</button></span>`;
  }).join('');
}

function removeRecipient(idx) {
  encryptRecipientIds = encryptRecipientIds.filter(i => i !== idx);
  renderEncryptChips();
}

// ── PGP Operations ─────────────────────────────────

async function doEncrypt() {
  const msg = $('#encrypt-message').value.trim();
  if (!msg) { showToast('Please enter a message'); return; }
  if (encryptRecipientIds.length === 0) { showToast('Please select at least one recipient'); return; }

  const resultEl = $('#encrypt-result');
  resultEl.innerHTML = '<div class="spinner" style="margin:14px auto;display:block;"></div>';

  try {
    const pubKeys = [];
    for (const idx of encryptRecipientIds) {
      const k = keys[idx];
      const parsed = await openpgp.readKey({ armoredKey: k.publicArmor });
      pubKeys.push(parsed);
    }

    const message = await openpgp.createMessage({ text: msg });
    const encrypted = await openpgp.encrypt({
      message,
      encryptionKeys: pubKeys,
    });

    resultEl.innerHTML = `
      <div class="result-panel success">
        <div class="result-header">✅ Encrypted Successfully</div>
        <div class="result-body">${esc(encrypted)}</div>
        <button class="copy-btn" style="margin-top:8px" onclick="copyText(this, ${JSON.stringify(encrypted).replace(/"/g, '&quot;')})">📋 Copy</button>
      </div>
    `;
  } catch (err) {
    resultEl.innerHTML = `<div class="result-panel error"><div class="result-header">❌ Error</div><p style="font-size:13px">${esc(err.message)}</p></div>`;
  }
}

async function doDecrypt() {
  const armored = $('#decrypt-message').value.trim();
  if (!armored) { showToast('Please paste an encrypted PGP message'); return; }

  const passphrase = $('#decrypt-passphrase').value;
  const selectedIdx = $('#decrypt-key').value;
  const resultEl = $('#decrypt-result');
  resultEl.innerHTML = '<div class="spinner" style="margin:14px auto;display:block;"></div>';

  try {
    const message = await openpgp.readMessage({ armoredMessage: armored });

    // Try selected key or all private keys
    const tryKeys = selectedIdx !== ''
      ? [keys[parseInt(selectedIdx)]]
      : keys.filter(k => k.hasPrivate);

    let decrypted = null;
    for (const k of tryKeys) {
      try {
        let privKey = await openpgp.readPrivateKey({ armoredKey: k.privateArmor });
        if (!privKey.isDecrypted()) {
          privKey = await openpgp.decryptKey({ privateKey: privKey, passphrase });
        }
        const { data } = await openpgp.decrypt({ message, decryptionKeys: privKey });
        decrypted = data;
        break;
      } catch (e) { /* try next key */ }
    }

    if (decrypted === null) throw new Error('Could not decrypt with any available key. Check your passphrase or key selection.');

    resultEl.innerHTML = `
      <div class="result-panel success">
        <div class="result-header">✅ Decrypted Message</div>
        <div class="result-body">${esc(decrypted)}</div>
        <button class="copy-btn" style="margin-top:8px" onclick="copyText(this, ${JSON.stringify(decrypted).replace(/"/g, '&quot;')})">📋 Copy</button>
      </div>
    `;
  } catch (err) {
    resultEl.innerHTML = `<div class="result-panel error"><div class="result-header">❌ Error</div><p style="font-size:13px">${esc(err.message)}</p></div>`;
  }
}

async function doSign() {
  const msg = $('#sign-message').value.trim();
  const keyIdx = $('#sign-key').value;
  if (!msg) { showToast('Please enter a message to sign'); return; }
  if (keyIdx === '') { showToast('Please select a signing key'); return; }

  const passphrase = $('#sign-passphrase').value;
  const resultEl = $('#sign-result');
  resultEl.innerHTML = '<div class="spinner" style="margin:14px auto;display:block;"></div>';

  try {
    const k = keys[parseInt(keyIdx)];
    let privKey = await openpgp.readPrivateKey({ armoredKey: k.privateArmor });
    if (!privKey.isDecrypted()) {
      privKey = await openpgp.decryptKey({ privateKey: privKey, passphrase });
    }

    const message = await openpgp.createCleartextMessage({ text: msg });
    const signed = await openpgp.sign({ message, signingKeys: privKey });

    resultEl.innerHTML = `
      <div class="result-panel success">
        <div class="result-header">✅ Signed Successfully</div>
        <div class="result-body">${esc(signed)}</div>
        <button class="copy-btn" style="margin-top:8px" onclick="copyText(this, ${JSON.stringify(signed).replace(/"/g, '&quot;')})">📋 Copy</button>
      </div>
    `;
  } catch (err) {
    resultEl.innerHTML = `<div class="result-panel error"><div class="result-header">❌ Error</div><p style="font-size:13px">${esc(err.message)}</p></div>`;
  }
}

async function doVerify() {
  const armored = $('#verify-message').value.trim();
  if (!armored) { showToast('Please paste a signed PGP message'); return; }

  const resultEl = $('#verify-result');
  resultEl.innerHTML = '<div class="spinner" style="margin:14px auto;display:block;"></div>';

  try {
    const message = await openpgp.readCleartextMessage({ cleartextMessage: armored });
    const pubKeys = [];
    for (const k of keys) {
      try {
        const pk = await openpgp.readKey({ armoredKey: k.publicArmor });
        pubKeys.push(pk);
      } catch (e) { /* skip bad keys */ }
    }

    const result = await openpgp.verify({ message, verificationKeys: pubKeys });
    const { verified, keyID } = result.signatures[0];

    try {
      await verified;
      const signerKeyId = keyID.toHex().toUpperCase();
      resultEl.innerHTML = `
        <div class="result-panel success">
          <div class="result-header"><span class="verify-badge valid">✅ Valid Signature</span></div>
          <p style="font-size:13px;margin-top:8px;color:var(--fg-muted)">Signed by key ID: <code>${signerKeyId}</code></p>
          <div class="result-body" style="margin-top:8px">${esc(result.data)}</div>
        </div>
      `;
    } catch (e) {
      resultEl.innerHTML = `
        <div class="result-panel error">
          <div class="result-header"><span class="verify-badge invalid">❌ Invalid Signature</span></div>
          <p style="font-size:13px;margin-top:8px;color:var(--fg-muted)">${esc(e.message)}</p>
        </div>
      `;
    }
  } catch (err) {
    resultEl.innerHTML = `<div class="result-panel error"><div class="result-header">❌ Error</div><p style="font-size:13px">${esc(err.message)}</p></div>`;
  }
}

// ── Key Generation ─────────────────────────────────
let selectedKeyType = 'ecc';

function initKeyGen() {
  $$('.keygen-opt').forEach(btn => {
    btn.addEventListener('click', () => {
      $$('.keygen-opt').forEach(b => b.classList.remove('selected'));
      btn.classList.add('selected');
      selectedKeyType = btn.dataset.type;
    });
  });
}

async function doGenKey() {
  const name = $('#genkey-name').value.trim();
  const email = $('#genkey-email').value.trim();
  if (!name || !email) { showToast('Please enter a name and email'); return; }

  const passphrase = $('#genkey-passphrase').value || undefined;
  const resultEl = $('#genkey-result');
  resultEl.innerHTML = '<div style="text-align:center;padding:14px"><div class="spinner"></div><p style="margin-top:10px;font-size:13px;color:var(--fg-muted)">Generating key pair…</p></div>';

  try {
    const opts = {
      userIDs: [{ name, email }],
      passphrase,
    };

    if (selectedKeyType === 'ecc') {
      opts.type = 'ecc';
      opts.curve = 'curve25519';
    } else {
      opts.type = 'rsa';
      opts.rsaBits = 4096;
    }

    const { privateKey, publicKey } = await openpgp.generateKey(opts);

    // Parse fingerprint
    const parsed = await openpgp.readKey({ armoredKey: publicKey });
    const fp = parsed.getFingerprint().toUpperCase();

    keys.push({
      label: `${name} <${email}>`,
      fingerprint: fp,
      publicArmor: publicKey,
      privateArmor: privateKey,
      hasPrivate: true,
    });
    saveKeys();

    resultEl.innerHTML = `<div class="result-panel success"><div class="result-header">✅ Key Pair Generated</div><p style="font-size:12px;color:var(--fg-muted)">Fingerprint: ${fp.slice(0,4)}…${fp.slice(-4)}</p></div>`;
    showToast('Key pair generated successfully');
  } catch (err) {
    resultEl.innerHTML = `<div class="result-panel error"><div class="result-header">❌ Error</div><p style="font-size:13px">${esc(err.message)}</p></div>`;
  }
}

// ── Key Import ─────────────────────────────────────
async function doImportKey(armor) {
  if (!armor) { showToast('Please paste a key'); return; }

  const resultEl = $('#import-result');
  resultEl.innerHTML = '<div class="spinner" style="margin:14px auto;display:block;"></div>';

  try {
    let publicArmor, privateArmor, hasPrivate = false, label, fp;

    // Try as private key first
    try {
      const privKey = await openpgp.readPrivateKey({ armoredKey: armor });
      privateArmor = armor;
      publicArmor = privKey.toPublic().armor();
      hasPrivate = true;
      const uids = privKey.getUserIDs();
      label = uids[0] || 'Imported Key';
      fp = privKey.getFingerprint().toUpperCase();
    } catch (e) {
      // Try as public key
      const pubKey = await openpgp.readKey({ armoredKey: armor });
      publicArmor = armor;
      const uids = pubKey.getUserIDs();
      label = uids[0] || 'Imported Key';
      fp = pubKey.getFingerprint().toUpperCase();
    }

    keys.push({ label, fingerprint: fp, publicArmor, privateArmor, hasPrivate });
    saveKeys();

    resultEl.innerHTML = `<div class="result-panel success"><div class="result-header">✅ Key Imported</div><p style="font-size:12px;color:var(--fg-muted)">${esc(label)}<br>Type: ${hasPrivate ? 'Key Pair (public + private)' : 'Public Key Only'}</p></div>`;
    showToast('Key imported successfully');
  } catch (err) {
    resultEl.innerHTML = `<div class="result-panel error"><div class="result-header">❌ Error</div><p style="font-size:13px">${esc(err.message)}</p></div>`;
  }
}

function initImport() {
  $('#import-file-btn').addEventListener('click', () => {
    $('#import-file-input').click();
  });

  $('#import-file-input').addEventListener('change', (e) => {
    const file = e.target.files[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = () => {
      $('#import-armor').value = reader.result;
    };
    reader.readAsText(file);
    e.target.value = '';
  });
}

// ── Key Export / Delete ────────────────────────────
function exportKey(idx) {
  const k = keys[idx];
  const content = $('#export-content');

  let html = `
    <div class="field">
      <label>Public Key</label>
      <textarea readonly rows="6" style="font-size:11px">${esc(k.publicArmor)}</textarea>
      <button class="copy-btn" style="margin-top:6px" onclick="copyText(this, keys[${idx}].publicArmor)">📋 Copy Public Key</button>
    </div>
  `;

  if (k.hasPrivate) {
    html += `
      <div class="field" style="margin-top:12px">
        <label>Private Key</label>
        <textarea readonly rows="6" style="font-size:11px">${esc(k.privateArmor)}</textarea>
        <button class="copy-btn" style="margin-top:6px" onclick="copyText(this, keys[${idx}].privateArmor)">📋 Copy Private Key</button>
      </div>
    `;
  }

  content.innerHTML = html;
  openDialog('dlg-export');
}

function deleteKey(idx) {
  const k = keys[idx];
  if (!confirm(`Delete "${k.label}"? This cannot be undone.`)) return;
  keys.splice(idx, 1);
  saveKeys();
  showToast('Key deleted');
}

// ── Copy to Clipboard ──────────────────────────────
async function copyText(btn, text) {
  try {
    await navigator.clipboard.writeText(text);
    btn.classList.add('copied');
    const orig = btn.innerHTML;
    btn.innerHTML = '✅ Copied';
    setTimeout(() => {
      btn.classList.remove('copied');
      btn.innerHTML = orig;
    }, 1500);
  } catch (e) {
    showToast('Failed to copy');
  }
}

// ── Wire Event Listeners ───────────────────────────
function initEvents() {
  // Key management
  $('#btn-gen-key').addEventListener('click', () => {
    // Reset form
    $('#genkey-name').value = '';
    $('#genkey-email').value = '';
    $('#genkey-passphrase').value = '';
    $('#genkey-result').innerHTML = '';
    openDialog('dlg-genkey');
  });
  $('#btn-import-key').addEventListener('click', () => {
    $('#import-armor').value = '';
    $('#import-result').innerHTML = '';
    openDialog('dlg-import');
  });

  // PGP actions
  $('#encrypt-submit').addEventListener('click', doEncrypt);
  $('#decrypt-submit').addEventListener('click', doDecrypt);
  $('#sign-submit').addEventListener('click', doSign);
  $('#verify-submit').addEventListener('click', doVerify);
  $('#genkey-submit').addEventListener('click', doGenKey);
  $('#import-submit').addEventListener('click', () => doImportKey($('#import-armor').value.trim()));

  // Clear results when opening dialogs
  $$('.action-card').forEach(card => {
    card.addEventListener('click', () => {
      const action = card.dataset.action;
      $(`#${action}-result`).innerHTML = '';
      if (action === 'encrypt') {
        encryptRecipientIds = [];
        renderEncryptChips();
      }
    });
  });
}

// ── Init ───────────────────────────────────────────
document.addEventListener('DOMContentLoaded', () => {
  applyTheme(currentTheme);
  initTabs();
  initDialogs();
  initCharCounters();
  initEncryptRecipients();
  initKeyGen();
  initImport();
  initEvents();
  renderKeys();
  refreshKeySelectors();
  updateNoKeysWarning();
});
