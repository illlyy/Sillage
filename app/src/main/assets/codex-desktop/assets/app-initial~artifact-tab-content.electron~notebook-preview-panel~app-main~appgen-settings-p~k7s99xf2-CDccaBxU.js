import { n as e, s as t } from "./rolldown-runtime-Czos8NxU.js";
import {
  AO as n,
  EI as r,
  Gu as i,
  HD as a,
  Hu as o,
  IO as s,
  Im as c,
  JF as l,
  Ku as u,
  Mk as d,
  Mm as f,
  Nk as p,
  Nm as m,
  Pm as h,
  QO as g,
  TI as ee,
  Uu as te,
  VD as ne,
  VN as re,
  YN as ie,
  eD as ae,
  jk as oe,
  kO as se,
  lF as ce,
  lw as le,
  nD as ue,
  rD as de,
  rF as fe,
  sl as pe,
  tD as me,
  uw as he,
  vI as ge,
  xl as _e,
  zF as ve,
  zO as ye,
} from "./app-initial~app-main~hotkey-window-thread-page~thread-app-shell-chrome~header~remote-conver~h59fr3q5-Cm3GYhJA.js";
import {
  ai as be,
  ii as xe,
} from "./app-initial~app-main~hotkey-window-new-thread-page~hotkey-window-home-page~composer-utility-bar-D6Thfq3g.js";
import {
  On as Se,
  kn as Ce,
} from "./app-initial~app-main~projects-index-page~hotkey-window-thread-page~thread-app-shell-chrome~~bg7586oi-Cs6pZQzU.js";
import {
  l as we,
  u as Te,
} from "./app-initial~artifact-tab-content.electron~notebook-preview-panel~app-main~first-run~appgen-~b6d67wz0-CEoOWcsq.js";
function Ee() {
  let e = (0, De.c)(3),
    t = ce(u),
    n;
  return (
    e[0] !== t.data || e[1] !== t.isLoading
      ? ((n = { data: t.data, isLoading: t.isLoading }),
        (e[0] = t.data),
        (e[1] = t.isLoading),
        (e[2] = n))
      : (n = e[2]),
    n
  );
}
var De,
  Oe = e(() => {
    ((De = ee()), fe(), i());
  });
function ke() {
  let e = (0, je.c)(7),
    { data: t, isLoading: n } = Ee(),
    r,
    i;
  if (e[0] !== t?.platform) {
    let n = o(t?.platform);
    ((r = n), (i = Ae(n)), (e[0] = t?.platform), (e[1] = r), (e[2] = i));
  } else ((r = e[1]), (i = e[2]));
  let a;
  return (
    e[3] !== n || e[4] !== r || e[5] !== i
      ? ((a = { platform: r, modifierSymbol: i, isLoading: n }),
        (e[3] = n),
        (e[4] = r),
        (e[5] = i),
        (e[6] = a))
      : (a = e[6]),
    a
  );
}
function Ae(e) {
  return e === `macOS` ? `⌘` : `^`;
}
var je,
  Me = e(() => {
    ((je = ee()), Oe(), te());
  }),
  _,
  v = e(() => {
    _ = typeof __SENTRY_DEBUG__ > `u` || __SENTRY_DEBUG__;
  }),
  y,
  b = e(() => {
    y = globalThis;
  }),
  Ne,
  Pe = e(() => {
    Ne = `10.29.0`;
  });
function Fe() {
  return (Ie(y), y);
}
function Ie(e) {
  let t = (e.__SENTRY__ = e.__SENTRY__ || {});
  return ((t.version = t.version || `10.29.0`), (t[Ne] = t[`10.29.0`] || {}));
}
function Le(e, t, n = y) {
  let r = (n.__SENTRY__ = n.__SENTRY__ || {}),
    i = (r[Ne] = r[`10.29.0`] || {});
  return i[e] || (i[e] = t());
}
var x = e(() => {
  (Pe(), b());
});
function Re(e) {
  if (!(`console` in y)) return e();
  let t = y.console,
    n = {},
    r = Object.keys(Ye);
  r.forEach((e) => {
    let r = Ye[e];
    ((n[e] = t[e]), (t[e] = r));
  });
  try {
    return e();
  } finally {
    r.forEach((e) => {
      t[e] = n[e];
    });
  }
}
function ze() {
  Ke().enabled = !0;
}
function Be() {
  Ke().enabled = !1;
}
function Ve() {
  return Ke().enabled;
}
function He(...e) {
  Ge(`log`, ...e);
}
function Ue(...e) {
  Ge(`warn`, ...e);
}
function We(...e) {
  Ge(`error`, ...e);
}
function Ge(e, ...t) {
  _ &&
    Ve() &&
    Re(() => {
      y.console[e](`${Je}[${e}]:`, ...t);
    });
}
function Ke() {
  return _ ? Le(`loggerSettings`, () => ({ enabled: !1 })) : { enabled: !1 };
}
var qe,
  Je,
  Ye,
  S,
  C = e(() => {
    (x(),
      v(),
      b(),
      (qe = [`debug`, `info`, `warn`, `error`, `log`, `assert`, `trace`]),
      (Je = `Sentry Logger `),
      (Ye = {}),
      (S = {
        enable: ze,
        disable: Be,
        isEnabled: Ve,
        log: He,
        warn: Ue,
        error: We,
      }));
  });
function Xe(...e) {
  let t = e.sort((e, t) => e[0] - t[0]).map((e) => e[1]);
  return (e, n = 0, r = 0) => {
    let i = [],
      a = e.split(`
`);
    for (let e = n; e < a.length; e++) {
      let n = a[e];
      n.length > 1024 && (n = n.slice(0, 1024));
      let o = it.test(n) ? n.replace(it, `$1`) : n;
      if (!o.match(/\S*Error: /)) {
        for (let e of t) {
          let t = e(o);
          if (t) {
            i.push(t);
            break;
          }
        }
        if (i.length >= rt + r) break;
      }
    }
    return Qe(i.slice(r));
  };
}
function Ze(e) {
  return Array.isArray(e) ? Xe(...e) : e;
}
function Qe(e) {
  if (!e.length) return [];
  let t = Array.from(e);
  return (
    /sentryWrapped/.test($e(t).function || ``) && t.pop(),
    t.reverse(),
    at.test($e(t).function || ``) &&
      (t.pop(), at.test($e(t).function || ``) && t.pop()),
    t
      .slice(0, rt)
      .map((e) => ({
        ...e,
        filename: e.filename || $e(t).filename,
        function: e.function || `?`,
      }))
  );
}
function $e(e) {
  return e[e.length - 1] || {};
}
function et(e) {
  try {
    return !e || typeof e != `function` ? ot : e.name || ot;
  } catch {
    return ot;
  }
}
function tt(e) {
  let t = e.exception;
  if (t) {
    let e = [];
    try {
      return (
        t.values.forEach((t) => {
          t.stacktrace.frames && e.push(...t.stacktrace.frames);
        }),
        e
      );
    } catch {
      return;
    }
  }
}
function nt(e) {
  return `__v_isVNode` in e && e.__v_isVNode ? `[VueVNode]` : `[VueViewModel]`;
}
var rt,
  it,
  at,
  ot,
  st = e(() => {
    ((rt = 50),
      (it = /\(error: (.*)\)/),
      (at = /captureMessage|captureException/),
      (ot = `<anonymous>`));
  });
function ct(e, t) {
  ((ut[e] = ut[e] || []), ut[e].push(t));
}
function lt(e, t) {
  if (!dt[e]) {
    dt[e] = !0;
    try {
      t();
    } catch (t) {
      _ && S.error(`Error while instrumenting ${e}`, t);
    }
  }
}
function w(e, t) {
  let n = e && ut[e];
  if (n)
    for (let r of n)
      try {
        r(t);
      } catch (t) {
        _ &&
          S.error(
            `Error while triggering instrumentation handler.\nType: ${e}\nName: ${et(r)}\nError:`,
            t,
          );
      }
}
var ut,
  dt,
  ft = e(() => {
    (v(), C(), st(), (ut = {}), (dt = {}));
  });
function pt(e) {
  let t = `error`;
  (ct(t, e), lt(t, mt));
}
function mt() {
  ((ht = y.onerror),
    (y.onerror = function (e, t, n, r, i) {
      return (
        w(`error`, { column: r, error: i, line: n, msg: e, url: t }),
        ht ? ht.apply(this, arguments) : !1
      );
    }),
    (y.onerror.__SENTRY_INSTRUMENTED__ = !0));
}
var ht,
  gt = e(() => {
    (b(), ft(), (ht = null));
  });
function _t(e) {
  let t = `unhandledrejection`;
  (ct(t, e), lt(t, vt));
}
function vt() {
  ((yt = y.onunhandledrejection),
    (y.onunhandledrejection = function (e) {
      return (w(`unhandledrejection`, e), yt ? yt.apply(this, arguments) : !0);
    }),
    (y.onunhandledrejection.__SENTRY_INSTRUMENTED__ = !0));
}
var yt,
  bt = e(() => {
    (b(), ft(), (yt = null));
  });
function xt(e) {
  switch (Rt.call(e)) {
    case `[object Error]`:
    case `[object Exception]`:
    case `[object DOMException]`:
    case `[object WebAssembly.Exception]`:
      return !0;
    default:
      return Ft(e, Error);
  }
}
function St(e, t) {
  return Rt.call(e) === `[object ${t}]`;
}
function Ct(e) {
  return St(e, `ErrorEvent`);
}
function wt(e) {
  return St(e, `DOMError`);
}
function Tt(e) {
  return St(e, `DOMException`);
}
function Et(e) {
  return St(e, `String`);
}
function Dt(e) {
  return (
    typeof e == `object` &&
    !!e &&
    `__sentry_template_string__` in e &&
    `__sentry_template_values__` in e
  );
}
function Ot(e) {
  return (
    e === null || Dt(e) || (typeof e != `object` && typeof e != `function`)
  );
}
function kt(e) {
  return St(e, `Object`);
}
function At(e) {
  return typeof Event < `u` && Ft(e, Event);
}
function jt(e) {
  return typeof Element < `u` && Ft(e, Element);
}
function Mt(e) {
  return St(e, `RegExp`);
}
function Nt(e) {
  return !!(e?.then && typeof e.then == `function`);
}
function Pt(e) {
  return (
    kt(e) &&
    `nativeEvent` in e &&
    `preventDefault` in e &&
    `stopPropagation` in e
  );
}
function Ft(e, t) {
  try {
    return e instanceof t;
  } catch {
    return !1;
  }
}
function It(e) {
  return !!(
    typeof e == `object` &&
    e &&
    (e.__isVue || e._isVue || e.__v_isVNode)
  );
}
function Lt(e) {
  return typeof Request < `u` && Ft(e, Request);
}
var Rt,
  T = e(() => {
    Rt = Object.prototype.toString;
  });
function E(e, t = {}) {
  if (!e) return `<unknown>`;
  try {
    let n = e,
      r = [],
      i = 0,
      a = 0,
      o,
      s = Array.isArray(t) ? t : t.keyAttrs,
      c = (!Array.isArray(t) && t.maxStringLength) || Ut;
    for (
      ;
      n &&
      i++ < 5 &&
      ((o = zt(n, s)),
      !(o === `html` || (i > 1 && a + r.length * 3 + o.length >= c)));
    )
      (r.push(o), (a += o.length), (n = n.parentNode));
    return r.reverse().join(` > `);
  } catch {
    return `<unknown>`;
  }
}
function zt(e, t) {
  let n = e,
    r = [];
  if (!n?.tagName) return ``;
  if (Ht.HTMLElement && n instanceof HTMLElement && n.dataset) {
    if (n.dataset.sentryComponent) return n.dataset.sentryComponent;
    if (n.dataset.sentryElement) return n.dataset.sentryElement;
  }
  r.push(n.tagName.toLowerCase());
  let i = t?.length
    ? t.filter((e) => n.getAttribute(e)).map((e) => [e, n.getAttribute(e)])
    : null;
  if (i?.length)
    i.forEach((e) => {
      r.push(`[${e[0]}="${e[1]}"]`);
    });
  else {
    n.id && r.push(`#${n.id}`);
    let e = n.className;
    if (e && Et(e)) {
      let t = e.split(/\s+/);
      for (let e of t) r.push(`.${e}`);
    }
  }
  for (let e of [`aria-label`, `type`, `name`, `title`, `alt`]) {
    let t = n.getAttribute(e);
    t && r.push(`[${e}="${t}"]`);
  }
  return r.join(``);
}
function Bt() {
  try {
    return Ht.document.location.href;
  } catch {
    return ``;
  }
}
function Vt(e) {
  if (!Ht.HTMLElement) return null;
  let t = e;
  for (let e = 0; e < 5; e++) {
    if (!t) return null;
    if (t instanceof HTMLElement) {
      if (t.dataset.sentryComponent) return t.dataset.sentryComponent;
      if (t.dataset.sentryElement) return t.dataset.sentryElement;
    }
    t = t.parentNode;
  }
  return null;
}
var Ht,
  Ut,
  Wt = e(() => {
    (T(), b(), (Ht = y), (Ut = 80));
  });
function D(e, t, n) {
  if (!(t in e)) return;
  let r = e[t];
  if (typeof r != `function`) return;
  let i = n(r);
  typeof i == `function` && Gt(i, r);
  try {
    e[t] = i;
  } catch {
    _ && S.log(`Failed to replace method "${t}" in object`, e);
  }
}
function O(e, t, n) {
  try {
    Object.defineProperty(e, t, { value: n, writable: !0, configurable: !0 });
  } catch {
    _ && S.log(`Failed to add non-enumerable property "${t}" to object`, e);
  }
}
function Gt(e, t) {
  try {
    ((e.prototype = t.prototype = t.prototype || {}),
      O(e, `__sentry_original__`, t));
  } catch {}
}
function Kt(e) {
  return e.__sentry_original__;
}
function qt(e) {
  if (xt(e))
    return { message: e.message, name: e.name, stack: e.stack, ...Yt(e) };
  if (At(e)) {
    let t = {
      type: e.type,
      target: Jt(e.target),
      currentTarget: Jt(e.currentTarget),
      ...Yt(e),
    };
    return (
      typeof CustomEvent < `u` && Ft(e, CustomEvent) && (t.detail = e.detail),
      t
    );
  } else return e;
}
function Jt(e) {
  try {
    return jt(e) ? E(e) : Object.prototype.toString.call(e);
  } catch {
    return `<unknown>`;
  }
}
function Yt(e) {
  if (typeof e == `object` && e) {
    let t = {};
    for (let n in e)
      Object.prototype.hasOwnProperty.call(e, n) && (t[n] = e[n]);
    return t;
  } else return {};
}
function Xt(e) {
  let t = Object.keys(qt(e));
  return (t.sort(), t[0] ? t.join(`, `) : `[object has no keys]`);
}
var Zt = e(() => {
  (v(), Wt(), C(), T());
});
function Qt(e, t = 0) {
  return typeof e != `string` || t === 0 || e.length <= t
    ? e
    : `${e.slice(0, t)}...`;
}
function $t(e, t) {
  if (!Array.isArray(e)) return ``;
  let n = [];
  for (let t = 0; t < e.length; t++) {
    let r = e[t];
    try {
      It(r) ? n.push(nt(r)) : n.push(String(r));
    } catch {
      n.push(`[value cannot be serialized]`);
    }
  }
  return n.join(t);
}
function en(e, t, n = !1) {
  return Et(e)
    ? Mt(t)
      ? t.test(e)
      : Et(t)
        ? n
          ? e === t
          : e.includes(t)
        : !1
    : !1;
}
function tn(e, t = [], n = !1) {
  return t.some((t) => en(e, t, n));
}
var nn = e(() => {
  (T(), st());
});
function rn() {
  let e = y;
  return e.crypto || e.msCrypto;
}
function an() {
  return Math.random() * 16;
}
function k(e = rn()) {
  try {
    if (e?.randomUUID) return e.randomUUID().replace(/-/g, ``);
  } catch {}
  return (
    (fn ||= `10000000100040008000100000000000`),
    fn.replace(/[018]/g, (e) => (e ^ ((an() & 15) >> (e / 4))).toString(16))
  );
}
function on(e) {
  return e.exception?.values?.[0];
}
function sn(e) {
  let { message: t, event_id: n } = e;
  if (t) return t;
  let r = on(e);
  return r
    ? r.type && r.value
      ? `${r.type}: ${r.value}`
      : r.type || r.value || n || `<unknown>`
    : n || `<unknown>`;
}
function cn(e, t, n) {
  let r = (e.exception = e.exception || {}),
    i = (r.values = r.values || []),
    a = (i[0] = i[0] || {});
  ((a.value ||= t || ``), (a.type ||= n || `Error`));
}
function ln(e, t) {
  let n = on(e);
  if (!n) return;
  let r = { type: `generic`, handled: !0 },
    i = n.mechanism;
  if (((n.mechanism = { ...r, ...i, ...t }), t && `data` in t)) {
    let e = { ...i?.data, ...t.data };
    n.mechanism.data = e;
  }
}
function un(e) {
  if (dn(e)) return !0;
  try {
    O(e, `__sentry_captured__`, !0);
  } catch {}
  return !1;
}
function dn(e) {
  try {
    return e.__sentry_captured__;
  } catch {}
}
var fn,
  pn = e(() => {
    (Zt(), b());
  });
function mn() {
  return Date.now() / _n;
}
function hn() {
  let { performance: e } = y;
  if (!e?.now || !e.timeOrigin) return mn;
  let t = e.timeOrigin;
  return () => (t + e.now()) / _n;
}
function A() {
  return (vn ??= hn())();
}
function gn() {
  let { performance: e } = y;
  if (!e?.now) return [void 0, `none`];
  let t = 3600 * 1e3,
    n = e.now(),
    r = Date.now(),
    i = e.timeOrigin ? Math.abs(e.timeOrigin + n - r) : t,
    a = i < t,
    o = e.timing?.navigationStart,
    s = typeof o == `number` ? Math.abs(o + n - r) : t;
  return a || s < t
    ? i <= s
      ? [e.timeOrigin, `timeOrigin`]
      : [o, `navigationStart`]
    : [r, `dateNow`];
}
function j() {
  return ((yn ||= gn()), yn[0]);
}
var _n,
  vn,
  yn,
  bn = e(() => {
    (b(), (_n = 1e3));
  });
function xn(e) {
  let t = A(),
    n = {
      sid: k(),
      init: !0,
      timestamp: t,
      started: t,
      duration: 0,
      status: `ok`,
      errors: 0,
      ignoreDuration: !1,
      toJSON: () => wn(n),
    };
  return (e && Sn(n, e), n);
}
function Sn(e, t = {}) {
  if (
    (t.user &&
      (!e.ipAddress && t.user.ip_address && (e.ipAddress = t.user.ip_address),
      !e.did &&
        !t.did &&
        (e.did = t.user.id || t.user.email || t.user.username)),
    (e.timestamp = t.timestamp || A()),
    t.abnormal_mechanism && (e.abnormal_mechanism = t.abnormal_mechanism),
    t.ignoreDuration && (e.ignoreDuration = t.ignoreDuration),
    t.sid && (e.sid = t.sid.length === 32 ? t.sid : k()),
    t.init !== void 0 && (e.init = t.init),
    !e.did && t.did && (e.did = `${t.did}`),
    typeof t.started == `number` && (e.started = t.started),
    e.ignoreDuration)
  )
    e.duration = void 0;
  else if (typeof t.duration == `number`) e.duration = t.duration;
  else {
    let t = e.timestamp - e.started;
    e.duration = t >= 0 ? t : 0;
  }
  (t.release && (e.release = t.release),
    t.environment && (e.environment = t.environment),
    !e.ipAddress && t.ipAddress && (e.ipAddress = t.ipAddress),
    !e.userAgent && t.userAgent && (e.userAgent = t.userAgent),
    typeof t.errors == `number` && (e.errors = t.errors),
    t.status && (e.status = t.status));
}
function Cn(e, t) {
  let n = {};
  (t ? (n = { status: t }) : e.status === `ok` && (n = { status: `exited` }),
    Sn(e, n));
}
function wn(e) {
  return {
    sid: `${e.sid}`,
    init: e.init,
    started: new Date(e.started * 1e3).toISOString(),
    timestamp: new Date(e.timestamp * 1e3).toISOString(),
    status: e.status,
    errors: e.errors,
    did:
      typeof e.did == `number` || typeof e.did == `string`
        ? `${e.did}`
        : void 0,
    duration: e.duration,
    abnormal_mechanism: e.abnormal_mechanism,
    attrs: {
      release: e.release,
      environment: e.environment,
      ip_address: e.ipAddress,
      user_agent: e.userAgent,
    },
  };
}
var Tn = e(() => {
  (pn(), bn());
});
function En(e, t, n = 2) {
  if (!t || typeof t != `object` || n <= 0) return t;
  if (e && Object.keys(t).length === 0) return e;
  let r = { ...e };
  for (let e in t)
    Object.prototype.hasOwnProperty.call(t, e) &&
      (r[e] = En(r[e], t[e], n - 1));
  return r;
}
var Dn = e(() => {});
function On() {
  return k();
}
function kn() {
  return k().substring(16);
}
var An = e(() => {
  pn();
});
function jn(e, t) {
  t ? O(e, Nn, t) : delete e[Nn];
}
function Mn(e) {
  return e[Nn];
}
var Nn,
  Pn = e(() => {
    (Zt(), (Nn = `_sentrySpan`));
  }),
  Fn,
  In,
  Ln = e(() => {
    (v(),
      Tn(),
      C(),
      T(),
      Dn(),
      pn(),
      An(),
      Pn(),
      nn(),
      bn(),
      (Fn = 100),
      (In = class e {
        constructor() {
          ((this._notifyingListeners = !1),
            (this._scopeListeners = []),
            (this._eventProcessors = []),
            (this._breadcrumbs = []),
            (this._attachments = []),
            (this._user = {}),
            (this._tags = {}),
            (this._attributes = {}),
            (this._extra = {}),
            (this._contexts = {}),
            (this._sdkProcessingMetadata = {}),
            (this._propagationContext = {
              traceId: On(),
              sampleRand: Math.random(),
            }));
        }
        clone() {
          let t = new e();
          return (
            (t._breadcrumbs = [...this._breadcrumbs]),
            (t._tags = { ...this._tags }),
            (t._attributes = { ...this._attributes }),
            (t._extra = { ...this._extra }),
            (t._contexts = { ...this._contexts }),
            this._contexts.flags &&
              (t._contexts.flags = {
                values: [...this._contexts.flags.values],
              }),
            (t._user = this._user),
            (t._level = this._level),
            (t._session = this._session),
            (t._transactionName = this._transactionName),
            (t._fingerprint = this._fingerprint),
            (t._eventProcessors = [...this._eventProcessors]),
            (t._attachments = [...this._attachments]),
            (t._sdkProcessingMetadata = { ...this._sdkProcessingMetadata }),
            (t._propagationContext = { ...this._propagationContext }),
            (t._client = this._client),
            (t._lastEventId = this._lastEventId),
            jn(t, Mn(this)),
            t
          );
        }
        setClient(e) {
          this._client = e;
        }
        setLastEventId(e) {
          this._lastEventId = e;
        }
        getClient() {
          return this._client;
        }
        lastEventId() {
          return this._lastEventId;
        }
        addScopeListener(e) {
          this._scopeListeners.push(e);
        }
        addEventProcessor(e) {
          return (this._eventProcessors.push(e), this);
        }
        setUser(e) {
          return (
            (this._user = e || {
              email: void 0,
              id: void 0,
              ip_address: void 0,
              username: void 0,
            }),
            this._session && Sn(this._session, { user: e }),
            this._notifyScopeListeners(),
            this
          );
        }
        getUser() {
          return this._user;
        }
        setTags(e) {
          return (
            (this._tags = { ...this._tags, ...e }),
            this._notifyScopeListeners(),
            this
          );
        }
        setTag(e, t) {
          return this.setTags({ [e]: t });
        }
        setAttributes(e) {
          return (
            (this._attributes = { ...this._attributes, ...e }),
            this._notifyScopeListeners(),
            this
          );
        }
        setAttribute(e, t) {
          return this.setAttributes({ [e]: t });
        }
        removeAttribute(e) {
          return (
            e in this._attributes &&
              (delete this._attributes[e], this._notifyScopeListeners()),
            this
          );
        }
        setExtras(e) {
          return (
            (this._extra = { ...this._extra, ...e }),
            this._notifyScopeListeners(),
            this
          );
        }
        setExtra(e, t) {
          return (
            (this._extra = { ...this._extra, [e]: t }),
            this._notifyScopeListeners(),
            this
          );
        }
        setFingerprint(e) {
          return ((this._fingerprint = e), this._notifyScopeListeners(), this);
        }
        setLevel(e) {
          return ((this._level = e), this._notifyScopeListeners(), this);
        }
        setTransactionName(e) {
          return (
            (this._transactionName = e),
            this._notifyScopeListeners(),
            this
          );
        }
        setContext(e, t) {
          return (
            t === null ? delete this._contexts[e] : (this._contexts[e] = t),
            this._notifyScopeListeners(),
            this
          );
        }
        setSession(e) {
          return (
            e ? (this._session = e) : delete this._session,
            this._notifyScopeListeners(),
            this
          );
        }
        getSession() {
          return this._session;
        }
        update(t) {
          if (!t) return this;
          let n = typeof t == `function` ? t(this) : t,
            {
              tags: r,
              attributes: i,
              extra: a,
              user: o,
              contexts: s,
              level: c,
              fingerprint: l = [],
              propagationContext: u,
            } = (n instanceof e ? n.getScopeData() : kt(n) ? t : void 0) || {};
          return (
            (this._tags = { ...this._tags, ...r }),
            (this._attributes = { ...this._attributes, ...i }),
            (this._extra = { ...this._extra, ...a }),
            (this._contexts = { ...this._contexts, ...s }),
            o && Object.keys(o).length && (this._user = o),
            c && (this._level = c),
            l.length && (this._fingerprint = l),
            u && (this._propagationContext = u),
            this
          );
        }
        clear() {
          return (
            (this._breadcrumbs = []),
            (this._tags = {}),
            (this._attributes = {}),
            (this._extra = {}),
            (this._user = {}),
            (this._contexts = {}),
            (this._level = void 0),
            (this._transactionName = void 0),
            (this._fingerprint = void 0),
            (this._session = void 0),
            jn(this, void 0),
            (this._attachments = []),
            this.setPropagationContext({
              traceId: On(),
              sampleRand: Math.random(),
            }),
            this._notifyScopeListeners(),
            this
          );
        }
        addBreadcrumb(e, t) {
          let n = typeof t == `number` ? t : Fn;
          if (n <= 0) return this;
          let r = {
            timestamp: mn(),
            ...e,
            message: e.message ? Qt(e.message, 2048) : e.message,
          };
          return (
            this._breadcrumbs.push(r),
            this._breadcrumbs.length > n &&
              ((this._breadcrumbs = this._breadcrumbs.slice(-n)),
              this._client?.recordDroppedEvent(`buffer_overflow`, `log_item`)),
            this._notifyScopeListeners(),
            this
          );
        }
        getLastBreadcrumb() {
          return this._breadcrumbs[this._breadcrumbs.length - 1];
        }
        clearBreadcrumbs() {
          return ((this._breadcrumbs = []), this._notifyScopeListeners(), this);
        }
        addAttachment(e) {
          return (this._attachments.push(e), this);
        }
        clearAttachments() {
          return ((this._attachments = []), this);
        }
        getScopeData() {
          return {
            breadcrumbs: this._breadcrumbs,
            attachments: this._attachments,
            contexts: this._contexts,
            tags: this._tags,
            attributes: this._attributes,
            extra: this._extra,
            user: this._user,
            level: this._level,
            fingerprint: this._fingerprint || [],
            eventProcessors: this._eventProcessors,
            propagationContext: this._propagationContext,
            sdkProcessingMetadata: this._sdkProcessingMetadata,
            transactionName: this._transactionName,
            span: Mn(this),
          };
        }
        setSDKProcessingMetadata(e) {
          return (
            (this._sdkProcessingMetadata = En(
              this._sdkProcessingMetadata,
              e,
              2,
            )),
            this
          );
        }
        setPropagationContext(e) {
          return ((this._propagationContext = e), this);
        }
        getPropagationContext() {
          return this._propagationContext;
        }
        captureException(e, t) {
          let n = t?.event_id || k();
          if (!this._client)
            return (
              _ &&
                S.warn(
                  `No client configured on scope - will not capture exception!`,
                ),
              n
            );
          let r = Error(`Sentry syntheticException`);
          return (
            this._client.captureException(
              e,
              {
                originalException: e,
                syntheticException: r,
                ...t,
                event_id: n,
              },
              this,
            ),
            n
          );
        }
        captureMessage(e, t, n) {
          let r = n?.event_id || k();
          if (!this._client)
            return (
              _ &&
                S.warn(
                  `No client configured on scope - will not capture message!`,
                ),
              r
            );
          let i = n?.syntheticException ?? Error(e);
          return (
            this._client.captureMessage(
              e,
              t,
              {
                originalException: e,
                syntheticException: i,
                ...n,
                event_id: r,
              },
              this,
            ),
            r
          );
        }
        captureEvent(e, t) {
          let n = t?.event_id || k();
          return this._client
            ? (this._client.captureEvent(e, { ...t, event_id: n }, this), n)
            : (_ &&
                S.warn(
                  `No client configured on scope - will not capture event!`,
                ),
              n);
        }
        _notifyScopeListeners() {
          this._notifyingListeners ||=
            ((this._notifyingListeners = !0),
            this._scopeListeners.forEach((e) => {
              e(this);
            }),
            !1);
        }
      }));
  });
function Rn() {
  return Le(`defaultCurrentScope`, () => new In());
}
function zn() {
  return Le(`defaultIsolationScope`, () => new In());
}
var Bn = e(() => {
  (x(), Ln());
});
function Vn() {
  let e = Ie(Fe());
  return (e.stack = e.stack || new Kn(Rn(), zn()));
}
function Hn(e) {
  return Vn().withScope(e);
}
function Un(e, t) {
  let n = Vn();
  return n.withScope(() => ((n.getStackTop().scope = e), t(e)));
}
function Wn(e) {
  return Vn().withScope(() => e(Vn().getIsolationScope()));
}
function Gn() {
  return {
    withIsolationScope: Wn,
    withScope: Hn,
    withSetScope: Un,
    withSetIsolationScope: (e, t) => Wn(t),
    getCurrentScope: () => Vn().getScope(),
    getIsolationScope: () => Vn().getIsolationScope(),
  };
}
var Kn,
  qn = e(() => {
    (Bn(),
      Ln(),
      T(),
      x(),
      (Kn = class {
        constructor(e, t) {
          let n;
          n = e || new In();
          let r;
          ((r = t || new In()),
            (this._stack = [{ scope: n }]),
            (this._isolationScope = r));
        }
        withScope(e) {
          let t = this._pushScope(),
            n;
          try {
            n = e(t);
          } catch (e) {
            throw (this._popScope(), e);
          }
          return Nt(n)
            ? n.then(
                (e) => (this._popScope(), e),
                (e) => {
                  throw (this._popScope(), e);
                },
              )
            : (this._popScope(), n);
        }
        getClient() {
          return this.getStackTop().client;
        }
        getScope() {
          return this.getStackTop().scope;
        }
        getIsolationScope() {
          return this._isolationScope;
        }
        getStackTop() {
          return this._stack[this._stack.length - 1];
        }
        _pushScope() {
          let e = this.getScope().clone();
          return (this._stack.push({ client: this.getClient(), scope: e }), e);
        }
        _popScope() {
          return this._stack.length <= 1 ? !1 : !!this._stack.pop();
        }
      }));
  });
function Jn(e) {
  let t = Ie(e);
  return t.acs ? t.acs : Gn();
}
var Yn = e(() => {
  (x(), qn());
});
function M() {
  return Jn(Fe()).getCurrentScope();
}
function N() {
  return Jn(Fe()).getIsolationScope();
}
function Xn() {
  return Le(`globalScope`, () => new In());
}
function Zn(...e) {
  let t = Jn(Fe());
  if (e.length === 2) {
    let [n, r] = e;
    return n ? t.withSetScope(n, r) : t.withScope(r);
  }
  return t.withScope(e[0]);
}
function P() {
  return M().getClient();
}
function Qn(e) {
  let {
      traceId: t,
      parentSpanId: n,
      propagationSpanId: r,
    } = e.getPropagationContext(),
    i = { trace_id: t, span_id: r || kn() };
  return (n && (i.parent_span_id = n), i);
}
var F = e(() => {
    (Yn(), x(), Ln(), An());
  }),
  I,
  $n,
  er,
  tr,
  L,
  nr,
  rr,
  ir,
  ar,
  or,
  sr,
  cr,
  lr = e(() => {
    ((I = `sentry.source`),
      ($n = `sentry.sample_rate`),
      (er = `sentry.previous_trace_sample_rate`),
      (tr = `sentry.op`),
      (L = `sentry.origin`),
      (nr = `sentry.idle_span_finish_reason`),
      (rr = `sentry.measurement_unit`),
      (ir = `sentry.measurement_value`),
      (ar = `sentry.custom_span_name`),
      (or = `sentry.profile_id`),
      (sr = `sentry.exclusive_time`),
      (cr = `sentry.link.type`));
  });
function ur(e) {
  if (e < 400 && e >= 100) return { code: 1 };
  if (e >= 400 && e < 500)
    switch (e) {
      case 401:
        return { code: 2, message: `unauthenticated` };
      case 403:
        return { code: 2, message: `permission_denied` };
      case 404:
        return { code: 2, message: `not_found` };
      case 409:
        return { code: 2, message: `already_exists` };
      case 413:
        return { code: 2, message: `failed_precondition` };
      case 429:
        return { code: 2, message: `resource_exhausted` };
      case 499:
        return { code: 2, message: `cancelled` };
      default:
        return { code: 2, message: `invalid_argument` };
    }
  if (e >= 500 && e < 600)
    switch (e) {
      case 501:
        return { code: 2, message: `unimplemented` };
      case 503:
        return { code: 2, message: `unavailable` };
      case 504:
        return { code: 2, message: `deadline_exceeded` };
      default:
        return { code: 2, message: `internal_error` };
    }
  return { code: 2, message: `internal_error` };
}
function dr(e, t) {
  e.setAttribute(`http.response.status_code`, t);
  let n = ur(t);
  n.message !== `unknown_error` && e.setStatus(n);
}
var fr = e(() => {});
function pr(e) {
  try {
    let t = y.WeakRef;
    if (typeof t == `function`) return new t(e);
  } catch {}
  return e;
}
function mr(e) {
  if (e) {
    if (typeof e == `object` && `deref` in e && typeof e.deref == `function`)
      try {
        return e.deref();
      } catch {
        return;
      }
    return e;
  }
}
function hr(e, t, n) {
  e && (O(e, vr, pr(n)), O(e, _r, t));
}
function gr(e) {
  let t = e;
  return { scope: t[_r], isolationScope: mr(t[vr]) };
}
var _r,
  vr,
  yr = e(() => {
    (Zt(), b(), (_r = `_sentryScope`), (vr = `_sentryIsolationScope`));
  });
function br(e) {
  let t = Sr(e);
  if (!t) return;
  let n = Object.entries(t).reduce((e, [t, n]) => {
    if (t.match(Er)) {
      let r = t.slice(7);
      e[r] = n;
    }
    return e;
  }, {});
  if (Object.keys(n).length > 0) return n;
}
function xr(e) {
  if (e)
    return wr(
      Object.entries(e).reduce(
        (e, [t, n]) => (n && (e[`${Tr}${t}`] = n), e),
        {},
      ),
    );
}
function Sr(e) {
  if (!(!e || (!Et(e) && !Array.isArray(e))))
    return Array.isArray(e)
      ? e.reduce((e, t) => {
          let n = Cr(t);
          return (
            Object.entries(n).forEach(([t, n]) => {
              e[t] = n;
            }),
            e
          );
        }, {})
      : Cr(e);
}
function Cr(e) {
  return e
    .split(`,`)
    .map((e) => {
      let t = e.indexOf(`=`);
      return t === -1
        ? []
        : [e.slice(0, t), e.slice(t + 1)].map((e) => {
            try {
              return decodeURIComponent(e.trim());
            } catch {
              return;
            }
          });
    })
    .reduce((e, [t, n]) => (t && n && (e[t] = n), e), {});
}
function wr(e) {
  if (Object.keys(e).length !== 0)
    return Object.entries(e).reduce((e, [t, n], r) => {
      let i = `${encodeURIComponent(t)}=${encodeURIComponent(n)}`,
        a = r === 0 ? i : `${e},${i}`;
      return a.length > 8192
        ? (_ &&
            S.warn(
              `Not adding key: ${t} with val: ${n} to baggage header due to exceeding baggage size limits.`,
            ),
          e)
        : a;
    }, ``);
}
var Tr,
  Er,
  Dr = e(() => {
    (v(), C(), T(), (Tr = `sentry-`), (Er = /^sentry-/));
  });
function Or(e) {
  return e === `http` || e === `https`;
}
function kr(e, t = !1) {
  let {
    host: n,
    path: r,
    pass: i,
    port: a,
    projectId: o,
    protocol: s,
    publicKey: c,
  } = e;
  return `${s}://${c}${t && i ? `:${i}` : ``}@${n}${a ? `:${a}` : ``}/${r && `${r}/`}${o}`;
}
function Ar(e) {
  let t = Lr.exec(e);
  if (!t) {
    Re(() => {
      console.error(`Invalid Sentry Dsn: ${e}`);
    });
    return;
  }
  let [n, r, i = ``, a = ``, o = ``, s = ``] = t.slice(1),
    c = ``,
    l = s,
    u = l.split(`/`);
  if ((u.length > 1 && ((c = u.slice(0, -1).join(`/`)), (l = u.pop())), l)) {
    let e = l.match(/^\d+/);
    e && (l = e[0]);
  }
  return jr({
    host: a,
    pass: i,
    path: c,
    projectId: l,
    port: o,
    protocol: n,
    publicKey: r,
  });
}
function jr(e) {
  return {
    protocol: e.protocol,
    publicKey: e.publicKey || ``,
    pass: e.pass || ``,
    host: e.host,
    port: e.port || ``,
    path: e.path || ``,
    projectId: e.projectId,
  };
}
function Mr(e) {
  if (!_) return !0;
  let { port: t, projectId: n, protocol: r } = e;
  return [`protocol`, `publicKey`, `host`, `projectId`].find((t) =>
    e[t] ? !1 : (S.error(`Invalid Sentry Dsn: ${t} missing`), !0),
  )
    ? !1
    : n.match(/^\d+$/)
      ? Or(r)
        ? t && isNaN(parseInt(t, 10))
          ? (S.error(`Invalid Sentry Dsn: Invalid port ${t}`), !1)
          : !0
        : (S.error(`Invalid Sentry Dsn: Invalid protocol ${r}`), !1)
      : (S.error(`Invalid Sentry Dsn: Invalid projectId ${n}`), !1);
}
function Nr(e) {
  return e.match(Ir)?.[1];
}
function Pr(e) {
  let t = e.getOptions(),
    { host: n } = e.getDsn() || {},
    r;
  return (t.orgId ? (r = String(t.orgId)) : n && (r = Nr(n)), r);
}
function Fr(e) {
  let t = typeof e == `string` ? Ar(e) : jr(e);
  if (!(!t || !Mr(t))) return t;
}
var Ir,
  Lr,
  Rr = e(() => {
    (v(),
      C(),
      (Ir = /^o(\d+)\./),
      (Lr = /^(?:(\w+):)\/\/(?:(\w+)(?::(\w+)?)?@)([\w.-]+)(?::(\d+))?\/(.+)/));
  });
function zr(e) {
  if (typeof e == `boolean`) return Number(e);
  let t = typeof e == `string` ? parseFloat(e) : e;
  if (!(typeof t != `number` || isNaN(t) || t < 0 || t > 1)) return t;
}
var Br = e(() => {});
function Vr(e) {
  if (!e) return;
  let t = e.match(Kr);
  if (!t) return;
  let n;
  return (
    t[3] === `1` ? (n = !0) : t[3] === `0` && (n = !1),
    { traceId: t[1], parentSampled: n, parentSpanId: t[2] }
  );
}
function Hr(e, t) {
  let n = Vr(e),
    r = br(t);
  if (!n?.traceId) return { traceId: On(), sampleRand: Math.random() };
  let i = Gr(n, r);
  r && (r.sample_rand = i.toString());
  let { traceId: a, parentSpanId: o, parentSampled: s } = n;
  return {
    traceId: a,
    parentSpanId: o,
    sampled: s,
    dsc: r || {},
    sampleRand: i,
  };
}
function Ur(e = On(), t = kn(), n) {
  let r = ``;
  return (n !== void 0 && (r = n ? `-1` : `-0`), `${e}-${t}${r}`);
}
function Wr(e = On(), t = kn(), n) {
  return `00-${e}-${t}-${n ? `01` : `00`}`;
}
function Gr(e, t) {
  let n = zr(t?.sample_rand);
  if (n !== void 0) return n;
  let r = zr(t?.sample_rate);
  return r && e?.parentSampled !== void 0
    ? e.parentSampled
      ? Math.random() * r
      : r + Math.random() * (1 - r)
    : Math.random();
}
var Kr,
  qr = e(() => {
    (Dr(),
      Br(),
      An(),
      (Kr = RegExp(
        `^[ \\t]*([0-9a-f]{32})?-?([0-9a-f]{16})?-?([01])?[ \\t]*$`,
      )));
  });
function Jr(e) {
  let { spanId: t, traceId: n } = e.spanContext(),
    {
      data: r,
      op: i,
      parent_span_id: a,
      status: o,
      origin: s,
      links: c,
    } = R(e);
  return {
    parent_span_id: a,
    span_id: t,
    trace_id: n,
    data: r,
    op: i,
    status: o,
    origin: s,
    links: c,
  };
}
function Yr(e) {
  let { spanId: t, traceId: n, isRemote: r } = e.spanContext(),
    i = r ? t : R(e).parent_span_id,
    a = gr(e).scope;
  return {
    parent_span_id: i,
    span_id: r ? a?.getPropagationContext().propagationSpanId || kn() : t,
    trace_id: n,
  };
}
function Xr(e) {
  let { traceId: t, spanId: n } = e.spanContext();
  return Ur(t, n, ri(e));
}
function Zr(e) {
  let { traceId: t, spanId: n } = e.spanContext();
  return Wr(t, n, ri(e));
}
function Qr(e) {
  if (e && e.length > 0)
    return e.map(
      ({
        context: { spanId: e, traceId: t, traceFlags: n, ...r },
        attributes: i,
      }) => ({
        span_id: e,
        trace_id: t,
        sampled: n === 1,
        attributes: i,
        ...r,
      }),
    );
}
function $r(e) {
  return typeof e == `number`
    ? ei(e)
    : Array.isArray(e)
      ? e[0] + e[1] / 1e9
      : e instanceof Date
        ? ei(e.getTime())
        : A();
}
function ei(e) {
  return e > 9999999999 ? e / 1e3 : e;
}
function R(e) {
  if (ni(e)) return e.getSpanJSON();
  let { spanId: t, traceId: n } = e.spanContext();
  if (ti(e)) {
    let {
      attributes: r,
      startTime: i,
      name: a,
      endTime: o,
      status: s,
      links: c,
    } = e;
    return {
      span_id: t,
      trace_id: n,
      data: r,
      description: a,
      parent_span_id:
        `parentSpanId` in e
          ? e.parentSpanId
          : `parentSpanContext` in e
            ? e.parentSpanContext?.spanId
            : void 0,
      start_timestamp: $r(i),
      timestamp: $r(o) || void 0,
      status: ii(s),
      op: r[tr],
      origin: r[L],
      links: Qr(c),
    };
  }
  return { span_id: t, trace_id: n, start_timestamp: 0, data: {} };
}
function ti(e) {
  let t = e;
  return (
    !!t.attributes && !!t.startTime && !!t.name && !!t.endTime && !!t.status
  );
}
function ni(e) {
  return typeof e.getSpanJSON == `function`;
}
function ri(e) {
  let { traceFlags: t } = e.spanContext();
  return t === 1;
}
function ii(e) {
  if (!(!e || e.code === 0))
    return e.code === 1 ? `ok` : e.message || `internal_error`;
}
function ai(e, t) {
  (O(t, di, e[di] || e), e[ui] ? e[ui].add(t) : O(e, ui, new Set([t])));
}
function oi(e, t) {
  e[ui] && e[ui].delete(t);
}
function si(e) {
  let t = new Set();
  function n(e) {
    if (!t.has(e) && ri(e)) {
      t.add(e);
      let r = e[ui] ? Array.from(e[ui]) : [];
      for (let e of r) n(e);
    }
  }
  return (n(e), Array.from(t));
}
function z(e) {
  return e[di] || e;
}
function B() {
  let e = Jn(Fe());
  return e.getActiveSpan ? e.getActiveSpan() : Mn(M());
}
function ci() {
  li ||=
    (Re(() => {
      console.warn(
        "[Sentry] Returning null from `beforeSendSpan` is disallowed. To drop certain spans, configure the respective integrations directly or use `ignoreSpans`.",
      );
    }),
    !0);
}
var li,
  ui,
  di,
  V = e(() => {
    (Yn(),
      x(),
      F(),
      lr(),
      fr(),
      yr(),
      Zt(),
      An(),
      bn(),
      qr(),
      C(),
      Pn(),
      (li = !1),
      (ui = `_sentryChildSpans`),
      (di = `_sentryRootSpan`));
  });
function fi() {
  if (pi) return;
  function e() {
    let e = B(),
      t = e && z(e);
    if (t) {
      let e = `internal_error`;
      (_ && S.log(`[Tracing] Root span: ${e} -> Global error occurred`),
        t.setStatus({ code: 2, message: e }));
    }
  }
  ((e.tag = `sentry_tracingErrorCallback`), (pi = !0), pt(e), _t(e));
}
var pi,
  mi = e(() => {
    (v(), gt(), bt(), C(), V(), fr(), (pi = !1));
  });
function H(e) {
  if (typeof __SENTRY_TRACING__ == `boolean` && !__SENTRY_TRACING__) return !1;
  let t = e || P()?.getOptions();
  return !!t && (t.tracesSampleRate != null || !!t.tracesSampler);
}
var hi = e(() => {
  F();
});
function gi(e) {
  S.log(
    `Ignoring span ${e.op} - ${e.description} because it matches \`ignoreSpans\`.`,
  );
}
function _i(e, t) {
  if (!t?.length || !e.description) return !1;
  for (let n of t) {
    if (yi(n)) {
      if (en(e.description, n)) return (_ && gi(e), !0);
      continue;
    }
    if (!n.name && !n.op) continue;
    let t = n.name ? en(e.description, n.name) : !0,
      r = n.op ? e.op && en(e.op, n.op) : !0;
    if (t && r) return (_ && gi(e), !0);
  }
  return !1;
}
function vi(e, t) {
  let n = t.parent_span_id,
    r = t.span_id;
  if (n) for (let t of e) t.parent_span_id === r && (t.parent_span_id = n);
}
function yi(e) {
  return typeof e == `string` || e instanceof RegExp;
}
var bi = e(() => {
    (v(), C(), nn());
  }),
  xi,
  Si = e(() => {
    xi = `production`;
  });
function Ci(e, t) {
  O(e, Di, t);
}
function wi(e, t) {
  let n = t.getOptions(),
    { publicKey: r } = t.getDsn() || {},
    i = {
      environment: n.environment || `production`,
      release: n.release,
      public_key: r,
      trace_id: e,
      org_id: Pr(t),
    };
  return (t.emit(`createDsc`, i), i);
}
function Ti(e, t) {
  let n = t.getPropagationContext();
  return n.dsc || wi(n.traceId, e);
}
function Ei(e) {
  let t = P();
  if (!t) return {};
  let n = z(e),
    r = R(n),
    i = r.data,
    a = n.spanContext().traceState,
    o =
      a?.get(`sentry.sample_rate`) ??
      i[`sentry.sample_rate`] ??
      i[`sentry.previous_trace_sample_rate`];
  function s(e) {
    return (
      (typeof o == `number` || typeof o == `string`) &&
        (e.sample_rate = `${o}`),
      e
    );
  }
  let c = n[Di];
  if (c) return s(c);
  let l = a?.get(`sentry.dsc`),
    u = l && br(l);
  if (u) return s(u);
  let d = wi(e.spanContext().traceId, t),
    f = i[I],
    p = r.description;
  return (
    f !== `url` && p && (d.transaction = p),
    H() &&
      ((d.sampled = String(ri(n))),
      (d.sample_rand =
        a?.get(`sentry.sample_rand`) ??
        gr(n).scope?.getPropagationContext().sampleRand.toString())),
    s(d),
    t.emit(`createDsc`, d, n),
    d
  );
}
var Di,
  Oi = e(() => {
    (Si(), F(), lr(), Dr(), Rr(), hi(), Zt(), V(), yr(), (Di = `_frozenDsc`));
  }),
  ki,
  Ai = e(() => {
    (An(),
      V(),
      (ki = class {
        constructor(e = {}) {
          ((this._traceId = e.traceId || On()),
            (this._spanId = e.spanId || kn()));
        }
        spanContext() {
          return {
            spanId: this._spanId,
            traceId: this._traceId,
            traceFlags: 0,
          };
        }
        end(e) {}
        setAttribute(e, t) {
          return this;
        }
        setAttributes(e) {
          return this;
        }
        setStatus(e) {
          return this;
        }
        updateName(e) {
          return this;
        }
        isRecording() {
          return !1;
        }
        addEvent(e, t, n) {
          return this;
        }
        addLink(e) {
          return this;
        }
        addLinks(e) {
          return this;
        }
        recordException(e, t) {}
      }));
  });
function ji(e, t = 100, n = 1 / 0) {
  try {
    return Ni(``, e, t, n);
  } catch (e) {
    return { ERROR: `**non-serializable** (${e})` };
  }
}
function Mi(e, t = 3, n = 100 * 1024) {
  let r = ji(e, t);
  return Li(r) > n ? Mi(e, t - 1, n) : r;
}
function Ni(e, t, n = 1 / 0, r = 1 / 0, i = Ri()) {
  let [a, o] = i;
  if (
    t == null ||
    [`boolean`, `string`].includes(typeof t) ||
    (typeof t == `number` && Number.isFinite(t))
  )
    return t;
  let s = Pi(e, t);
  if (!s.startsWith(`[object `)) return s;
  if (t.__sentry_skip_normalization__) return t;
  let c =
    typeof t.__sentry_override_normalization_depth__ == `number`
      ? t.__sentry_override_normalization_depth__
      : n;
  if (c === 0) return s.replace(`object `, ``);
  if (a(t)) return `[Circular ~]`;
  let l = t;
  if (l && typeof l.toJSON == `function`)
    try {
      return Ni(``, l.toJSON(), c - 1, r, i);
    } catch {}
  let u = Array.isArray(t) ? [] : {},
    d = 0,
    f = qt(t);
  for (let e in f) {
    if (!Object.prototype.hasOwnProperty.call(f, e)) continue;
    if (d >= r) {
      u[e] = `[MaxProperties ~]`;
      break;
    }
    let t = f[e];
    ((u[e] = Ni(e, t, c - 1, r, i)), d++);
  }
  return (o(t), u);
}
function Pi(e, t) {
  try {
    if (e === `domain` && t && typeof t == `object` && t._events)
      return `[Domain]`;
    if (e === `domainEmitter`) return `[DomainEmitter]`;
    if (typeof global < `u` && t === global) return `[Global]`;
    if (typeof window < `u` && t === window) return `[Window]`;
    if (typeof document < `u` && t === document) return `[Document]`;
    if (It(t)) return nt(t);
    if (Pt(t)) return `[SyntheticEvent]`;
    if (typeof t == `number` && !Number.isFinite(t)) return `[${t}]`;
    if (typeof t == `function`) return `[Function: ${et(t)}]`;
    if (typeof t == `symbol`) return `[${String(t)}]`;
    if (typeof t == `bigint`) return `[BigInt: ${String(t)}]`;
    let n = Fi(t);
    return /^HTML(\w*)Element$/.test(n)
      ? `[HTMLElement: ${n}]`
      : `[object ${n}]`;
  } catch (e) {
    return `**non-serializable** (${e})`;
  }
}
function Fi(e) {
  let t = Object.getPrototypeOf(e);
  return t?.constructor ? t.constructor.name : `null prototype`;
}
function Ii(e) {
  return ~-encodeURI(e).split(/%..|./).length;
}
function Li(e) {
  return Ii(JSON.stringify(e));
}
function Ri() {
  let e = new WeakSet();
  function t(t) {
    return e.has(t) ? !0 : (e.add(t), !1);
  }
  function n(t) {
    e.delete(t);
  }
  return [t, n];
}
var zi = e(() => {
  (T(), Zt(), st());
});
function Bi(e, t = []) {
  return [e, t];
}
function Vi(e, t) {
  let [n, r] = e;
  return [n, [...r, t]];
}
function Hi(e, t) {
  let n = e[1];
  for (let e of n) {
    let n = e[0].type;
    if (t(e, n)) return !0;
  }
  return !1;
}
function Ui(e) {
  let t = Ie(y);
  return t.encodePolyfill ? t.encodePolyfill(e) : new TextEncoder().encode(e);
}
function Wi(e) {
  let [t, n] = e,
    r = JSON.stringify(t);
  function i(e) {
    typeof r == `string`
      ? (r = typeof e == `string` ? r + e : [Ui(r), e])
      : r.push(typeof e == `string` ? Ui(e) : e);
  }
  for (let e of n) {
    let [t, n] = e;
    if (
      (i(`\n${JSON.stringify(t)}\n`),
      typeof n == `string` || n instanceof Uint8Array)
    )
      i(n);
    else {
      let e;
      try {
        e = JSON.stringify(n);
      } catch {
        e = JSON.stringify(ji(n));
      }
      i(e);
    }
  }
  return typeof r == `string` ? r : Gi(r);
}
function Gi(e) {
  let t = e.reduce((e, t) => e + t.length, 0),
    n = new Uint8Array(t),
    r = 0;
  for (let t of e) (n.set(t, r), (r += t.length));
  return n;
}
function Ki(e) {
  return [{ type: `span` }, e];
}
function qi(e) {
  let t = typeof e.data == `string` ? Ui(e.data) : e.data;
  return [
    {
      type: `attachment`,
      length: t.length,
      filename: e.filename,
      content_type: e.contentType,
      attachment_type: e.attachmentType,
    },
    t,
  ];
}
function Ji(e) {
  return Zi[e];
}
function Yi(e) {
  if (!e?.sdk) return;
  let { name: t, version: n } = e.sdk;
  return { name: t, version: n };
}
function Xi(e, t, n, r) {
  let i = e.sdkProcessingMetadata?.dynamicSamplingContext;
  return {
    event_id: e.event_id,
    sent_at: new Date().toISOString(),
    ...(t && { sdk: t }),
    ...(!!n && r && { dsn: kr(r) }),
    ...(i && { trace: i }),
  };
}
var Zi,
  Qi = e(() => {
    (x(),
      Rr(),
      zi(),
      b(),
      (Zi = {
        session: `session`,
        sessions: `session`,
        attachment: `attachment`,
        transaction: `transaction`,
        event: `error`,
        client_report: `internal`,
        user_report: `default`,
        profile: `profile`,
        profile_chunk: `profile`,
        replay_event: `replay`,
        replay_recording: `replay`,
        check_in: `monitor`,
        feedback: `feedback`,
        span: `span`,
        raw_security: `security`,
        log: `log_item`,
        metric: `metric`,
        trace_metric: `metric`,
      }));
  });
function $i(e, t) {
  if (!t) return e;
  let n = e.sdk || {};
  return (
    (e.sdk = {
      ...n,
      name: n.name || t.name,
      version: n.version || t.version,
      integrations: [...(e.sdk?.integrations || []), ...(t.integrations || [])],
      packages: [...(e.sdk?.packages || []), ...(t.packages || [])],
      settings:
        e.sdk?.settings || t.settings
          ? { ...e.sdk?.settings, ...t.settings }
          : void 0,
    }),
    e
  );
}
function ea(e, t, n, r) {
  let i = Yi(n);
  return Bi(
    {
      sent_at: new Date().toISOString(),
      ...(i && { sdk: i }),
      ...(!!r && t && { dsn: kr(t) }),
    },
    [
      `aggregates` in e
        ? [{ type: `sessions` }, e]
        : [{ type: `session` }, e.toJSON()],
    ],
  );
}
function ta(e, t, n, r) {
  let i = Yi(n),
    a = e.type && e.type !== `replay_event` ? e.type : `event`;
  $i(e, n?.sdk);
  let o = Xi(e, i, r, t);
  return (delete e.sdkProcessingMetadata, Bi(o, [[{ type: a }, e]]));
}
function na(e, t) {
  function n(e) {
    return !!e.trace_id && !!e.public_key;
  }
  let r = Ei(e[0]),
    i = t?.getDsn(),
    a = t?.getOptions().tunnel,
    o = {
      sent_at: new Date().toISOString(),
      ...(n(r) && { trace: r }),
      ...(!!a && i && { dsn: kr(i) }),
    },
    { beforeSendSpan: s, ignoreSpans: c } = t?.getOptions() || {},
    l = c?.length ? e.filter((e) => !_i(R(e), c)) : e,
    u = e.length - l.length;
  u && t?.recordDroppedEvent(`before_send`, `span`, u);
  let d = s
      ? (e) => {
          let t = R(e);
          return s(t) || (ci(), t);
        }
      : R,
    f = [];
  for (let e of l) {
    let t = d(e);
    t && f.push(Ki(t));
  }
  return Bi(o, f);
}
var ra = e(() => {
  (Oi(), Rr(), Qi(), bi(), V());
});
function ia(e) {
  if (!_) return;
  let {
      description: t = `< unknown name >`,
      op: n = `< unknown op >`,
      parent_span_id: r,
    } = R(e),
    { spanId: i } = e.spanContext(),
    a = ri(e),
    o = z(e),
    s = o === e,
    c = `[Tracing] Starting ${a ? `sampled` : `unsampled`} ${s ? `root ` : ``}span`,
    l = [`op: ${n}`, `name: ${t}`, `ID: ${i}`];
  if ((r && l.push(`parent ID: ${r}`), !s)) {
    let { op: e, description: t } = R(o);
    (l.push(`root ID: ${o.spanContext().spanId}`),
      e && l.push(`root op: ${e}`),
      t && l.push(`root description: ${t}`));
  }
  S.log(`${c}
  ${l.join(`
  `)}`);
}
function aa(e) {
  if (!_) return;
  let { description: t = `< unknown name >`, op: n = `< unknown op >` } = R(e),
    { spanId: r } = e.spanContext(),
    i = `[Tracing] Finishing "${n}" ${z(e) === e ? `root ` : ``}span "${t}" with ID ${r}`;
  S.log(i);
}
var oa = e(() => {
  (v(), C(), V());
});
function sa(e, t, n, r = B()) {
  let i = r && z(r);
  i &&
    (_ &&
      S.log(`[Measurement] Setting measurement on root span: ${e} = ${t} ${n}`),
    i.addEvent(e, { [ir]: t, [rr]: n }));
}
function ca(e) {
  if (!e || e.length === 0) return;
  let t = {};
  return (
    e.forEach((e) => {
      let n = e.attributes || {},
        r = n[rr],
        i = n[ir];
      typeof r == `string` &&
        typeof i == `number` &&
        (t[e.name] = { value: i, unit: r });
    }),
    t
  );
}
var la = e(() => {
  (v(), lr(), C(), V());
});
function ua(e) {
  return (e && typeof e == `number`) || e instanceof Date || Array.isArray(e);
}
function da(e) {
  return !!e.start_timestamp && !!e.timestamp && !!e.span_id && !!e.trace_id;
}
function fa(e) {
  return e instanceof ha && e.isStandaloneSpan();
}
function pa(e) {
  let t = P();
  if (!t) return;
  let n = e[1];
  if (!n || n.length === 0) {
    t.recordDroppedEvent(`before_send`, `span`);
    return;
  }
  t.sendEnvelope(e);
}
var ma,
  ha,
  ga = e(() => {
    (F(),
      v(),
      ra(),
      lr(),
      C(),
      An(),
      V(),
      bn(),
      Oi(),
      oa(),
      la(),
      yr(),
      (ma = 1e3),
      (ha = class {
        constructor(e = {}) {
          ((this._traceId = e.traceId || On()),
            (this._spanId = e.spanId || kn()),
            (this._startTime = e.startTimestamp || A()),
            (this._links = e.links),
            (this._attributes = {}),
            this.setAttributes({ [L]: `manual`, [tr]: e.op, ...e.attributes }),
            (this._name = e.name),
            e.parentSpanId && (this._parentSpanId = e.parentSpanId),
            `sampled` in e && (this._sampled = e.sampled),
            e.endTimestamp && (this._endTime = e.endTimestamp),
            (this._events = []),
            (this._isStandaloneSpan = e.isStandalone),
            this._endTime && this._onSpanEnded());
        }
        addLink(e) {
          return (
            this._links ? this._links.push(e) : (this._links = [e]),
            this
          );
        }
        addLinks(e) {
          return (
            this._links ? this._links.push(...e) : (this._links = e),
            this
          );
        }
        recordException(e, t) {}
        spanContext() {
          let { _spanId: e, _traceId: t, _sampled: n } = this;
          return { spanId: e, traceId: t, traceFlags: n ? 1 : 0 };
        }
        setAttribute(e, t) {
          return (
            t === void 0
              ? delete this._attributes[e]
              : (this._attributes[e] = t),
            this
          );
        }
        setAttributes(e) {
          return (
            Object.keys(e).forEach((t) => this.setAttribute(t, e[t])),
            this
          );
        }
        updateStartTime(e) {
          this._startTime = $r(e);
        }
        setStatus(e) {
          return ((this._status = e), this);
        }
        updateName(e) {
          return ((this._name = e), this.setAttribute(I, `custom`), this);
        }
        end(e) {
          this._endTime ||
            ((this._endTime = $r(e)), aa(this), this._onSpanEnded());
        }
        getSpanJSON() {
          return {
            data: this._attributes,
            description: this._name,
            op: this._attributes[tr],
            parent_span_id: this._parentSpanId,
            span_id: this._spanId,
            start_timestamp: this._startTime,
            status: ii(this._status),
            timestamp: this._endTime,
            trace_id: this._traceId,
            origin: this._attributes[L],
            profile_id: this._attributes[or],
            exclusive_time: this._attributes[sr],
            measurements: ca(this._events),
            is_segment: (this._isStandaloneSpan && z(this) === this) || void 0,
            segment_id: this._isStandaloneSpan
              ? z(this).spanContext().spanId
              : void 0,
            links: Qr(this._links),
          };
        }
        isRecording() {
          return !this._endTime && !!this._sampled;
        }
        addEvent(e, t, n) {
          _ && S.log(`[Tracing] Adding an event to span:`, e);
          let r = ua(t) ? t : n || A(),
            i = ua(t) ? {} : t || {},
            a = { name: e, time: $r(r), attributes: i };
          return (this._events.push(a), this);
        }
        isStandaloneSpan() {
          return !!this._isStandaloneSpan;
        }
        _onSpanEnded() {
          let e = P();
          if (
            (e && e.emit(`spanEnd`, this),
            !(this._isStandaloneSpan || this === z(this)))
          )
            return;
          if (this._isStandaloneSpan) {
            this._sampled
              ? pa(na([this], e))
              : (_ &&
                  S.log(
                    `[Tracing] Discarding standalone span because its trace was not chosen to be sampled.`,
                  ),
                e && e.recordDroppedEvent(`sample_rate`, `span`));
            return;
          }
          let t = this._convertSpanToTransaction();
          t && (gr(this).scope || M()).captureEvent(t);
        }
        _convertSpanToTransaction() {
          if (!da(R(this))) return;
          this._name ||=
            (_ &&
              S.warn(
                "Transaction has no name, falling back to `<unlabeled transaction>`.",
              ),
            `<unlabeled transaction>`);
          let { scope: e, isolationScope: t } = gr(this),
            n = e?.getScopeData().sdkProcessingMetadata?.normalizedRequest;
          if (this._sampled !== !0) return;
          let r = si(this)
              .filter((e) => e !== this && !fa(e))
              .map((e) => R(e))
              .filter(da),
            i = this._attributes[I];
          (delete this._attributes[ar],
            r.forEach((e) => {
              delete e.data[ar];
            }));
          let a = {
              contexts: { trace: Jr(this) },
              spans:
                r.length > ma
                  ? r
                      .sort((e, t) => e.start_timestamp - t.start_timestamp)
                      .slice(0, ma)
                  : r,
              start_timestamp: this._startTime,
              timestamp: this._endTime,
              transaction: this._name,
              type: `transaction`,
              sdkProcessingMetadata: {
                capturedSpanScope: e,
                capturedSpanIsolationScope: t,
                dynamicSamplingContext: Ei(this),
              },
              request: n,
              ...(i && { transaction_info: { source: i } }),
            },
            o = ca(this._events);
          return (
            o &&
              Object.keys(o).length &&
              (_ &&
                S.log(
                  `[Measurements] Adding measurements to transaction event`,
                  JSON.stringify(o, void 0, 2),
                ),
              (a.measurements = o)),
            a
          );
        }
      }));
  });
function _a(e, t, n = () => {}, r = () => {}) {
  let i;
  try {
    i = e();
  } catch (e) {
    throw (t(e), n(), e);
  }
  return va(i, t, n, r);
}
function va(e, t, n, r) {
  return Nt(e)
    ? e.then(
        (e) => (n(), r(e), e),
        (e) => {
          throw (t(e), n(), e);
        },
      )
    : (n(), r(e), e);
}
var ya = e(() => {
  T();
});
function ba(e, t, n) {
  if (!H(e)) return [!1];
  let r, i;
  typeof e.tracesSampler == `function`
    ? ((i = e.tracesSampler({
        ...t,
        inheritOrSampleWith: (e) =>
          typeof t.parentSampleRate == `number`
            ? t.parentSampleRate
            : typeof t.parentSampled == `boolean`
              ? Number(t.parentSampled)
              : e,
      })),
      (r = !0))
    : t.parentSampled === void 0
      ? e.tracesSampleRate !== void 0 && ((i = e.tracesSampleRate), (r = !0))
      : (i = t.parentSampled);
  let a = zr(i);
  if (a === void 0)
    return (
      _ &&
        S.warn(
          `[Tracing] Discarding root span because of invalid sample rate. Sample rate must be a boolean or a number between 0 and 1. Got ${JSON.stringify(i)} of type ${JSON.stringify(typeof i)}.`,
        ),
      [!1]
    );
  if (!a)
    return (
      _ &&
        S.log(
          `[Tracing] Discarding transaction because ${typeof e.tracesSampler == `function` ? `tracesSampler returned 0 or false` : `a negative sampling decision was inherited or tracesSampleRate is set to 0`}`,
        ),
      [!1, a, r]
    );
  let o = n < a;
  return (
    o ||
      (_ &&
        S.log(
          `[Tracing] Discarding transaction because it's not included in the random sample (sampling rate = ${Number(i)})`,
        )),
    [o, a, r]
  );
}
var xa = e(() => {
  (v(), C(), hi(), Br());
});
function Sa(e, t) {
  let n = Da();
  if (n.startSpan) return n.startSpan(e, t);
  let r = Ea(e),
    { forceTransaction: i, parentSpan: a, scope: o } = e,
    s = o?.clone();
  return Zn(s, () =>
    ja(a)(() => {
      let n = M(),
        o = Aa(n, a),
        s =
          e.onlyIfParent && !o
            ? new ki()
            : Ta({
                parentSpan: o,
                spanArguments: r,
                forceTransaction: i,
                scope: n,
              });
      return (
        jn(n, s),
        _a(
          () => t(s),
          () => {
            let { status: e } = R(s);
            s.isRecording() &&
              (!e || e === `ok`) &&
              s.setStatus({ code: 2, message: `internal_error` });
          },
          () => {
            s.end();
          },
        )
      );
    }),
  );
}
function Ca(e) {
  let t = Da();
  if (t.startInactiveSpan) return t.startInactiveSpan(e);
  let n = Ea(e),
    { forceTransaction: r, parentSpan: i } = e;
  return (
    e.scope
      ? (t) => Zn(e.scope, t)
      : i === void 0
        ? (e) => e()
        : (e) => wa(i, e)
  )(() => {
    let t = M(),
      a = Aa(t, i);
    return e.onlyIfParent && !a
      ? new ki()
      : Ta({ parentSpan: a, spanArguments: n, forceTransaction: r, scope: t });
  });
}
function wa(e, t) {
  let n = Da();
  return n.withActiveSpan
    ? n.withActiveSpan(e, t)
    : Zn((n) => (jn(n, e || void 0), t(n)));
}
function Ta({
  parentSpan: e,
  spanArguments: t,
  forceTransaction: n,
  scope: r,
}) {
  if (!H()) {
    let r = new ki();
    return (
      (n || !e) &&
        Ci(r, {
          sampled: `false`,
          sample_rate: `0`,
          transaction: t.name,
          ...Ei(r),
        }),
      r
    );
  }
  let i = N(),
    a;
  if (e && !n) ((a = ka(e, r, t)), ai(e, a));
  else if (e) {
    let n = Ei(e),
      { traceId: i, spanId: o } = e.spanContext(),
      s = ri(e);
    ((a = Oa({ traceId: i, parentSpanId: o, ...t }, r, s)), Ci(a, n));
  } else {
    let {
      traceId: e,
      dsc: n,
      parentSpanId: o,
      sampled: s,
    } = { ...i.getPropagationContext(), ...r.getPropagationContext() };
    ((a = Oa({ traceId: e, parentSpanId: o, ...t }, r, s)), n && Ci(a, n));
  }
  return (ia(a), hr(a, r, i), a);
}
function Ea(e) {
  let t = { isStandalone: (e.experimental || {}).standalone, ...e };
  if (e.startTime) {
    let n = { ...t };
    return ((n.startTimestamp = $r(e.startTime)), delete n.startTime, n);
  }
  return t;
}
function Da() {
  return Jn(Fe());
}
function Oa(e, t, n) {
  let r = P(),
    i = r?.getOptions() || {},
    { name: a = `` } = e,
    o = { spanAttributes: { ...e.attributes }, spanName: a, parentSampled: n };
  r?.emit(`beforeSampling`, o, { decision: !1 });
  let s = o.parentSampled ?? n,
    c = o.spanAttributes,
    l = t.getPropagationContext(),
    [u, d, f] = t.getScopeData().sdkProcessingMetadata[Ma]
      ? [!1]
      : ba(
          i,
          {
            name: a,
            parentSampled: s,
            attributes: c,
            parentSampleRate: zr(l.dsc?.sample_rate),
          },
          l.sampleRand,
        ),
    p = new ha({
      ...e,
      attributes: { [I]: `custom`, [$n]: d !== void 0 && f ? d : void 0, ...c },
      sampled: u,
    });
  return (
    !u &&
      r &&
      (_ &&
        S.log(
          `[Tracing] Discarding root span because its trace was not chosen to be sampled.`,
        ),
      r.recordDroppedEvent(`sample_rate`, `transaction`)),
    r && r.emit(`spanStart`, p),
    p
  );
}
function ka(e, t, n) {
  let { spanId: r, traceId: i } = e.spanContext(),
    a = t.getScopeData().sdkProcessingMetadata[Ma] ? !1 : ri(e),
    o = a
      ? new ha({ ...n, parentSpanId: r, traceId: i, sampled: a })
      : new ki({ traceId: i });
  ai(e, o);
  let s = P();
  return (
    s && (s.emit(`spanStart`, o), n.endTimestamp && s.emit(`spanEnd`, o)),
    o
  );
}
function Aa(e, t) {
  if (t) return t;
  if (t === null) return;
  let n = Mn(e);
  if (!n) return;
  let r = P();
  return (r ? r.getOptions() : {}).parentSpanIsAlwaysRootSpan ? z(n) : n;
}
function ja(e) {
  return e === void 0 ? (e) => e() : (t) => wa(e, t);
}
var Ma,
  Na = e(() => {
    (Yn(),
      x(),
      F(),
      v(),
      lr(),
      C(),
      ya(),
      hi(),
      Br(),
      Pn(),
      V(),
      Oi(),
      oa(),
      xa(),
      Ai(),
      ga(),
      fr(),
      yr(),
      (Ma = `__SENTRY_SUPPRESS_TRACING__`));
  });
function Pa(e, t = {}) {
  let n = new Map(),
    r = !1,
    i,
    a = Ba,
    o = !t.disableAutoFinish,
    s = [],
    {
      idleTimeout: c = Ia.idleTimeout,
      finalTimeout: l = Ia.finalTimeout,
      childSpanTimeout: u = Ia.childSpanTimeout,
      beforeSpanEnd: d,
      trimIdleSpanEndTimestamp: f = !0,
    } = t,
    p = P();
  if (!p || !H()) {
    let e = new ki();
    return (Ci(e, { sample_rate: `0`, sampled: `false`, ...Ei(e) }), e);
  }
  let m = M(),
    h = B(),
    g = Fa(e);
  g.end = new Proxy(g.end, {
    apply(e, t, n) {
      if ((d && d(g), t instanceof ki)) return;
      let [r, ...i] = n,
        a = $r(r || A()),
        o = si(g).filter((e) => e !== g),
        s = R(g);
      if (!o.length || !f) return (ae(a), Reflect.apply(e, t, [a, ...i]));
      let c = p.getOptions().ignoreSpans,
        u = o?.reduce(
          (e, t) => {
            let n = R(t);
            return !n.timestamp || (c && _i(n, c))
              ? e
              : e
                ? Math.max(e, n.timestamp)
                : n.timestamp;
          },
          void 0,
        ),
        m = s.start_timestamp,
        h = Math.min(
          m ? m + l / 1e3 : 1 / 0,
          Math.max(m || -1 / 0, Math.min(a, u || 1 / 0)),
        );
      return (ae(h), Reflect.apply(e, t, [h, ...i]));
    },
  });
  function ee() {
    i &&= (clearTimeout(i), void 0);
  }
  function te(e) {
    (ee(),
      (i = setTimeout(() => {
        !r && n.size === 0 && o && ((a = Ra), g.end(e));
      }, c)));
  }
  function ne(e) {
    i = setTimeout(() => {
      !r && o && ((a = La), g.end(e));
    }, u);
  }
  function re(e) {
    (ee(), n.set(e, !0), ne(A() + u / 1e3));
  }
  function ie(e) {
    (n.has(e) && n.delete(e), n.size === 0 && te(A() + c / 1e3));
  }
  function ae(e) {
    ((r = !0), n.clear(), s.forEach((e) => e()), jn(m, h));
    let t = R(g),
      { start_timestamp: i } = t;
    if (!i) return;
    t.data[`sentry.idle_span_finish_reason`] || g.setAttribute(nr, a);
    let o = t.status;
    ((!o || o === `unknown`) && g.setStatus({ code: 1 }),
      S.log(`[Tracing] Idle span "${t.op}" finished`));
    let u = si(g).filter((e) => e !== g),
      d = 0;
    (u.forEach((t) => {
      t.isRecording() &&
        (t.setStatus({ code: 2, message: `cancelled` }),
        t.end(e),
        _ &&
          S.log(
            `[Tracing] Cancelling span since span ended early`,
            JSON.stringify(t, void 0, 2),
          ));
      let { timestamp: n = 0, start_timestamp: r = 0 } = R(t),
        i = r <= e,
        a = (l + c) / 1e3,
        o = n - r <= a;
      if (_) {
        let e = JSON.stringify(t, void 0, 2);
        i
          ? o ||
            S.log(
              `[Tracing] Discarding span since it finished after idle span final timeout`,
              e,
            )
          : S.log(
              `[Tracing] Discarding span since it happened after idle span was finished`,
              e,
            );
      }
      (!o || !i) && (oi(g, t), d++);
    }),
      d > 0 && g.setAttribute(`sentry.idle_span_discarded_spans`, d));
  }
  return (
    s.push(
      p.on(`spanStart`, (e) => {
        r ||
          e === g ||
          R(e).timestamp ||
          (e instanceof ha && e.isStandaloneSpan()) ||
          (si(g).includes(e) && re(e.spanContext().spanId));
      }),
    ),
    s.push(
      p.on(`spanEnd`, (e) => {
        r || ie(e.spanContext().spanId);
      }),
    ),
    s.push(
      p.on(`idleSpanEnableAutoFinish`, (e) => {
        e === g && ((o = !0), te(), n.size && ne());
      }),
    ),
    t.disableAutoFinish || te(),
    setTimeout(() => {
      r ||
        (g.setStatus({ code: 2, message: `deadline_exceeded` }),
        (a = za),
        g.end());
    }, l),
    g
  );
}
function Fa(e) {
  let t = Ca(e);
  return (jn(M(), t), _ && S.log(`[Tracing] Started span is an idle span`), t);
}
var Ia,
  La,
  Ra,
  za,
  Ba,
  Va = e(() => {
    (F(),
      v(),
      lr(),
      C(),
      hi(),
      bi(),
      Pn(),
      V(),
      bn(),
      Oi(),
      Ai(),
      ga(),
      fr(),
      Na(),
      (Ia = { idleTimeout: 1e3, finalTimeout: 3e4, childSpanTimeout: 15e3 }),
      (La = `heartbeatFailed`),
      (Ra = `idleTimeout`),
      (za = `finalTimeout`),
      (Ba = `externalFinish`));
  });
function Ha(e) {
  return new qa((t) => {
    t(e);
  });
}
function Ua(e) {
  return new qa((t, n) => {
    n(e);
  });
}
var Wa,
  Ga,
  Ka,
  qa,
  Ja = e(() => {
    (T(),
      (Wa = 0),
      (Ga = 1),
      (Ka = 2),
      (qa = class e {
        constructor(e) {
          ((this._state = Wa), (this._handlers = []), this._runExecutor(e));
        }
        then(t, n) {
          return new e((e, r) => {
            (this._handlers.push([
              !1,
              (n) => {
                if (!t) e(n);
                else
                  try {
                    e(t(n));
                  } catch (e) {
                    r(e);
                  }
              },
              (t) => {
                if (!n) r(t);
                else
                  try {
                    e(n(t));
                  } catch (e) {
                    r(e);
                  }
              },
            ]),
              this._executeHandlers());
          });
        }
        catch(e) {
          return this.then((e) => e, e);
        }
        finally(t) {
          return new e((e, n) => {
            let r, i;
            return this.then(
              (e) => {
                ((i = !1), (r = e), t && t());
              },
              (e) => {
                ((i = !0), (r = e), t && t());
              },
            ).then(() => {
              if (i) {
                n(r);
                return;
              }
              e(r);
            });
          });
        }
        _executeHandlers() {
          if (this._state === Wa) return;
          let e = this._handlers.slice();
          ((this._handlers = []),
            e.forEach((e) => {
              e[0] ||=
                (this._state === Ga && e[1](this._value),
                this._state === Ka && e[2](this._value),
                !0);
            }));
        }
        _runExecutor(e) {
          let t = (e, t) => {
              if (this._state === Wa) {
                if (Nt(t)) {
                  t.then(n, r);
                  return;
                }
                ((this._state = e), (this._value = t), this._executeHandlers());
              }
            },
            n = (e) => {
              t(Ga, e);
            },
            r = (e) => {
              t(Ka, e);
            };
          try {
            e(n, r);
          } catch (e) {
            r(e);
          }
        }
      }));
  });
function Ya(e, t, n, r = 0) {
  try {
    let i = Xa(t, n, e, r);
    return Nt(i) ? i : Ha(i);
  } catch (e) {
    return Ua(e);
  }
}
function Xa(e, t, n, r) {
  let i = n[r];
  if (!e || !i) return e;
  let a = i({ ...e }, t);
  return (
    _ && a === null && S.log(`Event processor "${i.id || `?`}" dropped event`),
    Nt(a) ? a.then((e) => Xa(e, t, n, r + 1)) : Xa(a, t, n, r + 1)
  );
}
var Za = e(() => {
  (v(), C(), T(), Ja());
});
function Qa(e, t) {
  let { fingerprint: n, span: r, breadcrumbs: i, sdkProcessingMetadata: a } = t;
  (to(e, t), r && io(e, r), ao(e, n), no(e, i), ro(e, a));
}
function $a(e, t) {
  let {
    extra: n,
    tags: r,
    user: i,
    contexts: a,
    level: o,
    sdkProcessingMetadata: s,
    breadcrumbs: c,
    fingerprint: l,
    eventProcessors: u,
    attachments: d,
    propagationContext: f,
    transactionName: p,
    span: m,
  } = t;
  (eo(e, `extra`, n),
    eo(e, `tags`, r),
    eo(e, `user`, i),
    eo(e, `contexts`, a),
    (e.sdkProcessingMetadata = En(e.sdkProcessingMetadata, s, 2)),
    o && (e.level = o),
    p && (e.transactionName = p),
    m && (e.span = m),
    c.length && (e.breadcrumbs = [...e.breadcrumbs, ...c]),
    l.length && (e.fingerprint = [...e.fingerprint, ...l]),
    u.length && (e.eventProcessors = [...e.eventProcessors, ...u]),
    d.length && (e.attachments = [...e.attachments, ...d]),
    (e.propagationContext = { ...e.propagationContext, ...f }));
}
function eo(e, t, n) {
  e[t] = En(e[t], n, 1);
}
function to(e, t) {
  let {
    extra: n,
    tags: r,
    user: i,
    contexts: a,
    level: o,
    transactionName: s,
  } = t;
  (Object.keys(n).length && (e.extra = { ...n, ...e.extra }),
    Object.keys(r).length && (e.tags = { ...r, ...e.tags }),
    Object.keys(i).length && (e.user = { ...i, ...e.user }),
    Object.keys(a).length && (e.contexts = { ...a, ...e.contexts }),
    o && (e.level = o),
    s && e.type !== `transaction` && (e.transaction = s));
}
function no(e, t) {
  let n = [...(e.breadcrumbs || []), ...t];
  e.breadcrumbs = n.length ? n : void 0;
}
function ro(e, t) {
  e.sdkProcessingMetadata = { ...e.sdkProcessingMetadata, ...t };
}
function io(e, t) {
  ((e.contexts = { trace: Yr(t), ...e.contexts }),
    (e.sdkProcessingMetadata = {
      dynamicSamplingContext: Ei(t),
      ...e.sdkProcessingMetadata,
    }));
  let n = R(z(t)).description;
  n && !e.transaction && e.type === `transaction` && (e.transaction = n);
}
function ao(e, t) {
  ((e.fingerprint = e.fingerprint
    ? Array.isArray(e.fingerprint)
      ? e.fingerprint
      : [e.fingerprint]
    : []),
    t && (e.fingerprint = e.fingerprint.concat(t)),
    e.fingerprint.length || delete e.fingerprint);
}
var oo = e(() => {
  (Oi(), Dn(), V());
});
function so(e) {
  let t = y._sentryDebugIds,
    n = y._debugIds;
  if (!t && !n) return {};
  let r = t ? Object.keys(t) : [],
    i = n ? Object.keys(n) : [];
  if (fo && r.length === lo && i.length === uo) return fo;
  ((lo = r.length), (uo = i.length), (fo = {}), (co ||= {}));
  let a = (t, n) => {
    for (let r of t) {
      let t = n[r],
        i = co?.[r];
      if (i && fo && t) ((fo[i[0]] = t), co && (co[r] = [i[0], t]));
      else if (t) {
        let n = e(r);
        for (let e = n.length - 1; e >= 0; e--) {
          let i = n[e]?.filename;
          if (i && fo && co) {
            ((fo[i] = t), (co[r] = [i, t]));
            break;
          }
        }
      }
    }
  };
  return (t && a(r, t), n && a(i, n), fo);
}
var co,
  lo,
  uo,
  fo,
  po = e(() => {
    b();
  });
function mo(e, t, n, r, i, a) {
  let { normalizeDepth: o = 3, normalizeMaxBreadth: s = 1e3 } = e,
    c = {
      ...t,
      event_id: t.event_id || n.event_id || k(),
      timestamp: t.timestamp || mn(),
    },
    l = n.integrations || e.integrations.map((e) => e.name);
  (ho(c, e),
    vo(c, l),
    i && i.emit(`applyFrameMetadata`, t),
    t.type === void 0 && go(c, e.stackParser));
  let u = bo(r, n.captureContext);
  n.mechanism && ln(c, n.mechanism);
  let d = i ? i.getEventProcessors() : [],
    f = Xn().getScopeData();
  (a && $a(f, a.getScopeData()), u && $a(f, u.getScopeData()));
  let p = [...(n.attachments || []), ...f.attachments];
  return (
    p.length && (n.attachments = p),
    Qa(c, f),
    Ya([...d, ...f.eventProcessors], c, n).then(
      (e) => (e && _o(e), typeof o == `number` && o > 0 ? yo(e, o, s) : e),
    )
  );
}
function ho(e, t) {
  let { environment: n, release: r, dist: i, maxValueLength: a } = t;
  ((e.environment = e.environment || n || `production`),
    !e.release && r && (e.release = r),
    !e.dist && i && (e.dist = i));
  let o = e.request;
  (o?.url && a && (o.url = Qt(o.url, a)),
    a &&
      e.exception?.values?.forEach((e) => {
        e.value &&= Qt(e.value, a);
      }));
}
function go(e, t) {
  let n = so(t);
  e.exception?.values?.forEach((e) => {
    e.stacktrace?.frames?.forEach((e) => {
      e.filename && (e.debug_id = n[e.filename]);
    });
  });
}
function _o(e) {
  let t = {};
  if (
    (e.exception?.values?.forEach((e) => {
      e.stacktrace?.frames?.forEach((e) => {
        e.debug_id &&
          (e.abs_path
            ? (t[e.abs_path] = e.debug_id)
            : e.filename && (t[e.filename] = e.debug_id),
          delete e.debug_id);
      });
    }),
    Object.keys(t).length === 0)
  )
    return;
  ((e.debug_meta = e.debug_meta || {}),
    (e.debug_meta.images = e.debug_meta.images || []));
  let n = e.debug_meta.images;
  Object.entries(t).forEach(([e, t]) => {
    n.push({ type: `sourcemap`, code_file: e, debug_id: t });
  });
}
function vo(e, t) {
  t.length > 0 &&
    ((e.sdk = e.sdk || {}),
    (e.sdk.integrations = [...(e.sdk.integrations || []), ...t]));
}
function yo(e, t, n) {
  if (!e) return null;
  let r = {
    ...e,
    ...(e.breadcrumbs && {
      breadcrumbs: e.breadcrumbs.map((e) => ({
        ...e,
        ...(e.data && { data: ji(e.data, t, n) }),
      })),
    }),
    ...(e.user && { user: ji(e.user, t, n) }),
    ...(e.contexts && { contexts: ji(e.contexts, t, n) }),
    ...(e.extra && { extra: ji(e.extra, t, n) }),
  };
  return (
    e.contexts?.trace &&
      r.contexts &&
      ((r.contexts.trace = e.contexts.trace),
      e.contexts.trace.data &&
        (r.contexts.trace.data = ji(e.contexts.trace.data, t, n))),
    e.spans &&
      (r.spans = e.spans.map((e) => ({
        ...e,
        ...(e.data && { data: ji(e.data, t, n) }),
      }))),
    e.contexts?.flags &&
      r.contexts &&
      (r.contexts.flags = ji(e.contexts.flags, 3, n)),
    r
  );
}
function bo(e, t) {
  if (!t) return e;
  let n = e ? e.clone() : new In();
  return (n.update(t), n);
}
function xo(e) {
  if (e) return So(e) || Co(e) ? { captureContext: e } : e;
}
function So(e) {
  return e instanceof In || typeof e == `function`;
}
function Co(e) {
  return Object.keys(e).some((e) => wo.includes(e));
}
var wo,
  To = e(() => {
    (Si(),
      F(),
      Za(),
      Ln(),
      oo(),
      po(),
      pn(),
      zi(),
      nn(),
      bn(),
      (wo = [
        `user`,
        `level`,
        `extra`,
        `contexts`,
        `tags`,
        `fingerprint`,
        `propagationContext`,
      ]));
  });
function Eo(e, t) {
  return M().captureException(e, xo(t));
}
function Do(e, t) {
  return M().captureEvent(e, t);
}
function Oo(e, t) {
  N().setTag(e, t);
}
function ko(e) {
  N().setUser(e);
}
function Ao() {
  let e = P();
  return e?.getOptions().enabled !== !1 && !!e?.getTransport();
}
function jo(e) {
  let t = N(),
    n = M(),
    { userAgent: r } = y.navigator || {},
    i = xn({
      user: n.getUser() || t.getUser(),
      ...(r && { userAgent: r }),
      ...e,
    }),
    a = t.getSession();
  return (
    a?.status === `ok` && Sn(a, { status: `exited` }),
    Mo(),
    t.setSession(i),
    i
  );
}
function Mo() {
  let e = N(),
    t = M().getSession() || e.getSession();
  (t && Cn(t), No(), e.setSession());
}
function No() {
  let e = N(),
    t = P(),
    n = e.getSession();
  n && t && t.captureSession(n);
}
function Po(e = !1) {
  if (e) {
    Mo();
    return;
  }
  No();
}
var Fo = e(() => {
  (F(), Tn(), To(), b());
});
function Io(e) {
  let t = e.protocol ? `${e.protocol}:` : ``,
    n = e.port ? `:${e.port}` : ``;
  return `${t}//${e.host}${n}${e.path ? `/${e.path}` : ``}/api/`;
}
function Lo(e) {
  return `${Io(e)}${e.projectId}/envelope/`;
}
function Ro(e, t) {
  let n = { sentry_version: Bo };
  return (
    e.publicKey && (n.sentry_key = e.publicKey),
    t && (n.sentry_client = `${t.name}/${t.version}`),
    new URLSearchParams(n).toString()
  );
}
function zo(e, t, n) {
  return t || `${Lo(e)}?${Ro(e, n)}`;
}
var Bo,
  Vo = e(() => {
    Bo = `7`;
  });
function Ho(e) {
  let t = {};
  return (
    e.forEach((e) => {
      let { name: n } = e,
        r = t[n];
      (r && !r.isDefaultInstance && e.isDefaultInstance) || (t[n] = e);
    }),
    Object.values(t)
  );
}
function Uo(e) {
  let t = e.defaultIntegrations || [],
    n = e.integrations;
  t.forEach((e) => {
    e.isDefaultInstance = !0;
  });
  let r;
  if (Array.isArray(n)) r = [...t, ...n];
  else if (typeof n == `function`) {
    let e = n(t);
    r = Array.isArray(e) ? e : [e];
  } else r = t;
  return Ho(r);
}
function Wo(e, t) {
  let n = {};
  return (
    t.forEach((t) => {
      t && Ko(e, t, n);
    }),
    n
  );
}
function Go(e, t) {
  for (let n of t) n?.afterAllSetup && n.afterAllSetup(e);
}
function Ko(e, t, n) {
  if (n[t.name]) {
    _ &&
      S.log(`Integration skipped because it was already installed: ${t.name}`);
    return;
  }
  if (
    ((n[t.name] = t),
    !Jo.includes(t.name) &&
      typeof t.setupOnce == `function` &&
      (t.setupOnce(), Jo.push(t.name)),
    t.setup && typeof t.setup == `function` && t.setup(e),
    typeof t.preprocessEvent == `function`)
  ) {
    let n = t.preprocessEvent.bind(t);
    e.on(`preprocessEvent`, (t, r) => n(t, r, e));
  }
  if (typeof t.processEvent == `function`) {
    let n = t.processEvent.bind(t),
      r = Object.assign((t, r) => n(t, r, e), { id: t.name });
    e.addEventProcessor(r);
  }
  _ && S.log(`Integration installed: ${t.name}`);
}
function qo(e) {
  return e;
}
var Jo,
  Yo = e(() => {
    (v(), C(), (Jo = []));
  });
function Xo(e) {
  return [
    {
      type: `log`,
      item_count: e.length,
      content_type: `application/vnd.sentry.items.log+json`,
    },
    { items: e },
  ];
}
function Zo(e, t, n, r) {
  let i = {};
  return (
    t?.sdk && (i.sdk = { name: t.sdk.name, version: t.sdk.version }),
    n && r && (i.dsn = kr(r)),
    Bi(i, [Xo(e)])
  );
}
var Qo = e(() => {
  (Rr(), Qi());
});
function $o(e, t) {
  let n = t ?? es(e) ?? [];
  if (n.length === 0) return;
  let r = e.getOptions(),
    i = Zo(n, r._metadata, r.tunnel, e.getDsn());
  (ts().set(e, []), e.emit(`flushLogs`), e.sendEnvelope(i));
}
function es(e) {
  return ts().get(e);
}
function ts() {
  return Le(`clientToLogBufferMap`, () => new WeakMap());
}
var ns = e(() => {
  (x(), Qo());
});
function rs(e) {
  return [
    {
      type: `trace_metric`,
      item_count: e.length,
      content_type: `application/vnd.sentry.items.trace-metric+json`,
    },
    { items: e },
  ];
}
function is(e, t, n, r) {
  let i = {};
  return (
    t?.sdk && (i.sdk = { name: t.sdk.name, version: t.sdk.version }),
    n && r && (i.dsn = kr(r)),
    Bi(i, [rs(e)])
  );
}
var as = e(() => {
  (Rr(), Qi());
});
function os(e, t) {
  let n = t ?? ss(e) ?? [];
  if (n.length === 0) return;
  let r = e.getOptions(),
    i = is(n, r._metadata, r.tunnel, e.getDsn());
  (cs().set(e, []), e.emit(`flushMetrics`), e.sendEnvelope(i));
}
function ss(e) {
  return cs().get(e);
}
function cs() {
  return Le(`clientToMetricBufferMap`, () => new WeakMap());
}
var ls = e(() => {
  (x(), as());
});
function us(e = 100) {
  let t = new Set();
  function n() {
    return t.size < e;
  }
  function r(e) {
    t.delete(e);
  }
  function i(e) {
    if (!n()) return Ua(ds);
    let i = e();
    return (
      t.add(i),
      i.then(
        () => r(i),
        () => r(i),
      ),
      i
    );
  }
  function a(e) {
    if (!t.size) return Ha(!0);
    let n = Promise.allSettled(Array.from(t)).then(() => !0);
    if (!e) return n;
    let r = [n, new Promise((t) => setTimeout(() => t(!1), e))];
    return Promise.race(r);
  }
  return {
    get $() {
      return Array.from(t);
    },
    add: i,
    drain: a,
  };
}
var ds,
  fs = e(() => {
    (Ja(), (ds = Symbol.for(`SentryBufferFullError`)));
  });
function ps(e, t = Date.now()) {
  let n = parseInt(`${e}`, 10);
  if (!isNaN(n)) return n * 1e3;
  let r = Date.parse(`${e}`);
  return isNaN(r) ? _s : r - t;
}
function ms(e, t) {
  return e[t] || e.all || 0;
}
function hs(e, t, n = Date.now()) {
  return ms(e, t) > n;
}
function gs(e, { statusCode: t, headers: n }, r = Date.now()) {
  let i = { ...e },
    a = n?.[`x-sentry-rate-limits`],
    o = n?.[`retry-after`];
  if (a)
    for (let e of a.trim().split(`,`)) {
      let [t, n, , , a] = e.split(`:`, 5),
        o = parseInt(t, 10),
        s = (isNaN(o) ? 60 : o) * 1e3;
      if (!n) i.all = r + s;
      else
        for (let e of n.split(`;`))
          e === `metric_bucket`
            ? (!a || a.split(`;`).includes(`custom`)) && (i[e] = r + s)
            : (i[e] = r + s);
    }
  else o ? (i.all = r + ps(o, r)) : t === 429 && (i.all = r + 60 * 1e3);
  return i;
}
var _s,
  vs = e(() => {
    _s = 60 * 1e3;
  });
function ys(e, t, n = us(e.bufferSize || 64)) {
  let r = {},
    i = (e) => n.drain(e);
  function a(i) {
    let a = [];
    if (
      (Hi(i, (t, n) => {
        let i = Ji(n);
        hs(r, i) ? e.recordDroppedEvent(`ratelimit_backoff`, i) : a.push(t);
      }),
      a.length === 0)
    )
      return Promise.resolve({});
    let o = Bi(i[0], a),
      s = (t) => {
        Hi(o, (n, r) => {
          e.recordDroppedEvent(t, Ji(r));
        });
      };
    return n
      .add(() =>
        t({ body: Wi(o) }).then(
          (e) => (
            e.statusCode !== void 0 &&
              (e.statusCode < 200 || e.statusCode >= 300) &&
              _ &&
              S.warn(
                `Sentry responded with status code ${e.statusCode} to sent event.`,
              ),
            (r = gs(r, e)),
            e
          ),
          (e) => {
            throw (
              s(`network_error`),
              _ && S.error(`Encountered error running transport request:`, e),
              e
            );
          },
        ),
      )
      .then(
        (e) => e,
        (e) => {
          if (e === ds)
            return (
              _ && S.error(`Skipped sending event because buffer is full.`),
              s(`queue_overflow`),
              Promise.resolve({})
            );
          throw e;
        },
      );
  }
  return { send: a, flush: i };
}
var bs = e(() => {
  (v(), C(), Qi(), fs(), vs());
});
function xs(e, t, n) {
  let r = [
    { type: `client_report` },
    { timestamp: n || mn(), discarded_events: e },
  ];
  return Bi(t ? { dsn: t } : {}, [r]);
}
var Ss = e(() => {
  (Qi(), bn());
});
function Cs(e) {
  let t = [];
  e.message && t.push(e.message);
  try {
    let n = e.exception.values[e.exception.values.length - 1];
    n?.value && (t.push(n.value), n.type && t.push(`${n.type}: ${n.value}`));
  } catch {}
  return t;
}
var ws = e(() => {});
function Ts(e) {
  let {
    trace_id: t,
    parent_span_id: n,
    span_id: r,
    status: i,
    origin: a,
    data: o,
    op: s,
  } = e.contexts?.trace ?? {};
  return {
    data: o ?? {},
    description: e.transaction,
    op: s,
    parent_span_id: n,
    span_id: r ?? ``,
    start_timestamp: e.start_timestamp ?? 0,
    status: i,
    timestamp: e.timestamp,
    trace_id: t ?? ``,
    origin: a,
    profile_id: o?.[or],
    exclusive_time: o?.[sr],
    measurements: e.measurements,
    is_segment: !0,
  };
}
function Es(e) {
  return {
    type: `transaction`,
    timestamp: e.timestamp,
    start_timestamp: e.start_timestamp,
    transaction: e.description,
    contexts: {
      trace: {
        trace_id: e.trace_id,
        span_id: e.span_id,
        parent_span_id: e.parent_span_id,
        op: e.op,
        status: e.status,
        origin: e.origin,
        data: {
          ...e.data,
          ...(e.profile_id && { "sentry.profile_id": e.profile_id }),
          ...(e.exclusive_time && {
            "sentry.exclusive_time": e.exclusive_time,
          }),
        },
      },
    },
    measurements: e.measurements,
  };
}
var Ds = e(() => {
  lr();
});
function Os(e) {
  return { message: e, [Ws]: !0 };
}
function ks(e) {
  return { message: e, [Gs]: !0 };
}
function As(e) {
  return !!e && typeof e == `object` && Ws in e;
}
function js(e) {
  return !!e && typeof e == `object` && Gs in e;
}
function Ms(e, t, n, r, i) {
  let a = 0,
    o,
    s = !1;
  (e.on(n, () => {
    ((a = 0), clearTimeout(o), (s = !1));
  }),
    e.on(t, (t) => {
      ((a += r(t)),
        a >= 8e5
          ? i(e)
          : s ||
            ((s = !0),
            (o = setTimeout(() => {
              i(e);
            }, Ks))));
    }),
    e.on(`flush`, () => {
      i(e);
    }));
}
function Ns(e) {
  return e === `replay_event` ? `replay` : e || `error`;
}
function Ps(e, t) {
  let n = `${t} must return \`null\` or a valid event.`;
  if (Nt(e))
    return e.then(
      (e) => {
        if (!kt(e) && e !== null) throw Os(n);
        return e;
      },
      (e) => {
        throw Os(`${t} rejected with ${e}`);
      },
    );
  if (!kt(e) && e !== null) throw Os(n);
  return e;
}
function Fs(e, t, n, r) {
  let {
      beforeSend: i,
      beforeSendTransaction: a,
      beforeSendSpan: o,
      ignoreSpans: s,
    } = t,
    c = n;
  if (Is(c) && i) return i(c, r);
  if (Ls(c)) {
    if (o || s) {
      let t = Ts(c);
      if (s?.length && _i(t, s)) return null;
      if (o) {
        let e = o(t);
        e ? (c = En(n, Es(e))) : ci();
      }
      if (c.spans) {
        let t = [],
          n = c.spans;
        for (let e of n) {
          if (s?.length && _i(e, s)) {
            vi(n, e);
            continue;
          }
          if (o) {
            let n = o(e);
            n ? t.push(n) : (ci(), t.push(e));
          } else t.push(e);
        }
        let r = c.spans.length - t.length;
        (r && e.recordDroppedEvent(`before_send`, `span`, r), (c.spans = t));
      }
    }
    if (a) {
      if (c.spans) {
        let e = c.spans.length;
        c.sdkProcessingMetadata = {
          ...n.sdkProcessingMetadata,
          spanCountBeforeProcessing: e,
        };
      }
      return a(c, r);
    }
  }
  return c;
}
function Is(e) {
  return e.type === void 0;
}
function Ls(e) {
  return e.type === `transaction`;
}
function Rs(e) {
  let t = 0;
  return (e.name && (t += e.name.length * 2), (t += 8), t + Bs(e.attributes));
}
function zs(e) {
  let t = 0;
  return (e.message && (t += e.message.length * 2), t + Bs(e.attributes));
}
function Bs(e) {
  if (!e) return 0;
  let t = 0;
  return (
    Object.values(e).forEach((e) => {
      Array.isArray(e)
        ? (t += e.length * Vs(e[0]))
        : Ot(e)
          ? (t += Vs(e))
          : (t += 100);
    }),
    t
  );
}
function Vs(e) {
  return typeof e == `string`
    ? e.length * 2
    : typeof e == `number`
      ? 8
      : typeof e == `boolean`
        ? 4
        : 0;
}
var Hs,
  Us,
  Ws,
  Gs,
  Ks,
  qs,
  Js = e(() => {
    (Vo(),
      Si(),
      F(),
      v(),
      ra(),
      Yo(),
      ns(),
      ls(),
      Tn(),
      Oi(),
      bs(),
      Ss(),
      C(),
      Rr(),
      Qi(),
      ws(),
      T(),
      Dn(),
      pn(),
      Br(),
      To(),
      fs(),
      bi(),
      V(),
      Ja(),
      Ds(),
      (Hs = `Not capturing exception because it's already been captured.`),
      (Us = `Discarded session because of missing or non-string release`),
      (Ws = Symbol.for(`SentryInternalError`)),
      (Gs = Symbol.for(`SentryDoNotSendEventError`)),
      (Ks = 5e3),
      (qs = class {
        constructor(e) {
          if (
            ((this._options = e),
            (this._integrations = {}),
            (this._numProcessing = 0),
            (this._outcomes = {}),
            (this._hooks = {}),
            (this._eventProcessors = []),
            (this._promiseBuffer = us(e.transportOptions?.bufferSize ?? 64)),
            e.dsn
              ? (this._dsn = Fr(e.dsn))
              : _ && S.warn(`No DSN provided, client will not send events.`),
            this._dsn)
          ) {
            let t = zo(
              this._dsn,
              e.tunnel,
              e._metadata ? e._metadata.sdk : void 0,
            );
            this._transport = e.transport({
              tunnel: this._options.tunnel,
              recordDroppedEvent: this.recordDroppedEvent.bind(this),
              ...e.transportOptions,
              url: t,
            });
          }
          ((this._options.enableLogs =
            this._options.enableLogs ?? this._options._experiments?.enableLogs),
            this._options.enableLogs &&
              Ms(this, `afterCaptureLog`, `flushLogs`, zs, $o),
            (this._options.enableMetrics ??
              this._options._experiments?.enableMetrics ??
              !0) &&
              Ms(this, `afterCaptureMetric`, `flushMetrics`, Rs, os));
        }
        captureException(e, t, n) {
          let r = k();
          if (un(e)) return (_ && S.log(Hs), r);
          let i = { event_id: r, ...t };
          return (
            this._process(
              () =>
                this.eventFromException(e, i)
                  .then((e) => this._captureEvent(e, i, n))
                  .then((e) => e),
              `error`,
            ),
            i.event_id
          );
        }
        captureMessage(e, t, n, r) {
          let i = { event_id: k(), ...n },
            a = Dt(e) ? e : String(e),
            o = Ot(e),
            s = o
              ? this.eventFromMessage(a, t, i)
              : this.eventFromException(e, i);
          return (
            this._process(
              () => s.then((e) => this._captureEvent(e, i, r)),
              o ? `unknown` : `error`,
            ),
            i.event_id
          );
        }
        captureEvent(e, t, n) {
          let r = k();
          if (t?.originalException && un(t.originalException))
            return (_ && S.log(Hs), r);
          let i = { event_id: r, ...t },
            a = e.sdkProcessingMetadata || {},
            o = a.capturedSpanScope,
            s = a.capturedSpanIsolationScope,
            c = Ns(e.type);
          return (
            this._process(() => this._captureEvent(e, i, o || n, s), c),
            i.event_id
          );
        }
        captureSession(e) {
          (this.sendSession(e), Sn(e, { init: !1 }));
        }
        getDsn() {
          return this._dsn;
        }
        getOptions() {
          return this._options;
        }
        getSdkMetadata() {
          return this._options._metadata;
        }
        getTransport() {
          return this._transport;
        }
        async flush(e) {
          let t = this._transport;
          if (!t) return !0;
          this.emit(`flush`);
          let n = await this._isClientDoneProcessing(e),
            r = await t.flush(e);
          return n && r;
        }
        async close(e) {
          let t = await this.flush(e);
          return ((this.getOptions().enabled = !1), this.emit(`close`), t);
        }
        getEventProcessors() {
          return this._eventProcessors;
        }
        addEventProcessor(e) {
          this._eventProcessors.push(e);
        }
        init() {
          (this._isEnabled() ||
            this._options.integrations.some(({ name: e }) =>
              e.startsWith(`Spotlight`),
            )) &&
            this._setupIntegrations();
        }
        getIntegrationByName(e) {
          return this._integrations[e];
        }
        addIntegration(e) {
          let t = this._integrations[e.name];
          (Ko(this, e, this._integrations), t || Go(this, [e]));
        }
        sendEvent(e, t = {}) {
          this.emit(`beforeSendEvent`, e, t);
          let n = ta(
            e,
            this._dsn,
            this._options._metadata,
            this._options.tunnel,
          );
          for (let e of t.attachments || []) n = Vi(n, qi(e));
          this.sendEnvelope(n).then((t) => this.emit(`afterSendEvent`, e, t));
        }
        sendSession(e) {
          let { release: t, environment: n = xi } = this._options;
          if (`aggregates` in e) {
            let r = e.attrs || {};
            if (!r.release && !t) {
              _ && S.warn(Us);
              return;
            }
            ((r.release = r.release || t),
              (r.environment = r.environment || n),
              (e.attrs = r));
          } else {
            if (!e.release && !t) {
              _ && S.warn(Us);
              return;
            }
            ((e.release = e.release || t),
              (e.environment = e.environment || n));
          }
          this.emit(`beforeSendSession`, e);
          let r = ea(
            e,
            this._dsn,
            this._options._metadata,
            this._options.tunnel,
          );
          this.sendEnvelope(r);
        }
        recordDroppedEvent(e, t, n = 1) {
          if (this._options.sendClientReports) {
            let r = `${e}:${t}`;
            (_ &&
              S.log(`Recording outcome: "${r}"${n > 1 ? ` (${n} times)` : ``}`),
              (this._outcomes[r] = (this._outcomes[r] || 0) + n));
          }
        }
        on(e, t) {
          let n = (this._hooks[e] = this._hooks[e] || new Set()),
            r = (...e) => t(...e);
          return (
            n.add(r),
            () => {
              n.delete(r);
            }
          );
        }
        emit(e, ...t) {
          let n = this._hooks[e];
          n && n.forEach((e) => e(...t));
        }
        async sendEnvelope(e) {
          if (
            (this.emit(`beforeEnvelope`, e),
            this._isEnabled() && this._transport)
          )
            try {
              return await this._transport.send(e);
            } catch (e) {
              return (_ && S.error(`Error while sending envelope:`, e), {});
            }
          return (_ && S.error(`Transport disabled`), {});
        }
        _setupIntegrations() {
          let { integrations: e } = this._options;
          ((this._integrations = Wo(this, e)), Go(this, e));
        }
        _updateSessionFromEvent(e, t) {
          let n = t.level === `fatal`,
            r = !1,
            i = t.exception?.values;
          if (i) {
            ((r = !0), (n = !1));
            for (let e of i)
              if (e.mechanism?.handled === !1) {
                n = !0;
                break;
              }
          }
          let a = e.status === `ok`;
          ((a && e.errors === 0) || (a && n)) &&
            (Sn(e, {
              ...(n && { status: `crashed` }),
              errors: e.errors || Number(r || n),
            }),
            this.captureSession(e));
        }
        async _isClientDoneProcessing(e) {
          let t = 0;
          for (; !e || t < e; ) {
            if (
              (await new Promise((e) => setTimeout(e, 1)), !this._numProcessing)
            )
              return !0;
            t++;
          }
          return !1;
        }
        _isEnabled() {
          return this.getOptions().enabled !== !1 && this._transport !== void 0;
        }
        _prepareEvent(e, t, n, r) {
          let i = this.getOptions(),
            a = Object.keys(this._integrations);
          return (
            !t.integrations && a?.length && (t.integrations = a),
            this.emit(`preprocessEvent`, e, t),
            e.type || r.setLastEventId(e.event_id || t.event_id),
            mo(i, e, t, n, this, r).then((e) =>
              e === null
                ? e
                : (this.emit(`postprocessEvent`, e, t),
                  (e.contexts = { trace: Qn(n), ...e.contexts }),
                  (e.sdkProcessingMetadata = {
                    dynamicSamplingContext: Ti(this, n),
                    ...e.sdkProcessingMetadata,
                  }),
                  e),
            )
          );
        }
        _captureEvent(e, t = {}, n = M(), r = N()) {
          return (
            _ &&
              Is(e) &&
              S.log(`Captured error event \`${Cs(e)[0] || `<unknown>`}\``),
            this._processEvent(e, t, n, r).then(
              (e) => e.event_id,
              (e) => {
                _ &&
                  (js(e)
                    ? S.log(e.message)
                    : As(e)
                      ? S.warn(e.message)
                      : S.warn(e));
              },
            )
          );
        }
        _processEvent(e, t, n, r) {
          let i = this.getOptions(),
            { sampleRate: a } = i,
            o = Ls(e),
            s = Is(e),
            c = `before send for type \`${e.type || `error`}\``,
            l = a === void 0 ? void 0 : zr(a);
          if (s && typeof l == `number` && Math.random() > l)
            return (
              this.recordDroppedEvent(`sample_rate`, `error`),
              Ua(
                ks(
                  `Discarding event because it's not included in the random sample (sampling rate = ${a})`,
                ),
              )
            );
          let u = Ns(e.type);
          return this._prepareEvent(e, t, n, r)
            .then((e) => {
              if (e === null)
                throw (
                  this.recordDroppedEvent(`event_processor`, u),
                  ks("An event processor returned `null`, will not send event.")
                );
              return t.data && t.data.__sentry__ === !0
                ? e
                : Ps(Fs(this, i, e, t), c);
            })
            .then((i) => {
              if (i === null) {
                if ((this.recordDroppedEvent(`before_send`, u), o)) {
                  let t = 1 + (e.spans || []).length;
                  this.recordDroppedEvent(`before_send`, `span`, t);
                }
                throw ks(`${c} returned \`null\`, will not send event.`);
              }
              let a = n.getSession() || r.getSession();
              if ((s && a && this._updateSessionFromEvent(a, i), o)) {
                let e =
                  (i.sdkProcessingMetadata?.spanCountBeforeProcessing || 0) -
                  (i.spans ? i.spans.length : 0);
                e > 0 && this.recordDroppedEvent(`before_send`, `span`, e);
              }
              let l = i.transaction_info;
              return (
                o &&
                  l &&
                  i.transaction !== e.transaction &&
                  (i.transaction_info = { ...l, source: `custom` }),
                this.sendEvent(i, t),
                i
              );
            })
            .then(null, (e) => {
              throw js(e) || As(e)
                ? e
                : (this.captureException(e, {
                    mechanism: { handled: !1, type: `internal` },
                    data: { __sentry__: !0 },
                    originalException: e,
                  }),
                  Os(
                    `Event processing pipeline threw an error, original event will not be sent. Details have been sent as a new event.\nReason: ${e}`,
                  ));
            });
        }
        _process(e, t) {
          (this._numProcessing++,
            this._promiseBuffer.add(e).then(
              (e) => (this._numProcessing--, e),
              (e) => (
                this._numProcessing--,
                e === ds && this.recordDroppedEvent(`queue_overflow`, t),
                e
              ),
            ));
        }
        _clearOutcomes() {
          let e = this._outcomes;
          return (
            (this._outcomes = {}),
            Object.entries(e).map(([e, t]) => {
              let [n, r] = e.split(`:`);
              return { reason: n, category: r, quantity: t };
            })
          );
        }
        _flushOutcomes() {
          _ && S.log(`Flushing outcomes...`);
          let e = this._clearOutcomes();
          if (e.length === 0) {
            _ && S.log(`No outcomes to send`);
            return;
          }
          if (!this._dsn) {
            _ && S.log(`No dsn provided, will not send outcomes`);
            return;
          }
          _ && S.log(`Sending outcomes:`, e);
          let t = xs(e, this._options.tunnel && kr(this._dsn));
          this.sendEnvelope(t);
        }
      }));
  });
function Ys(e, t) {
  (t.debug === !0 &&
    (_
      ? S.enable()
      : Re(() => {
          console.warn(
            "[Sentry] Cannot initialize SDK with `debug` option using a non-debug bundle.",
          );
        })),
    M().update(t.initialScope));
  let n = new e(t);
  return (Xs(n), n.init(), n);
}
function Xs(e) {
  M().setClient(e);
}
var Zs = e(() => {
  (F(), v(), C());
});
function Qs(e) {
  return `isRelative` in e;
}
function $s(e, t) {
  let n = e.indexOf(`://`) <= 0 && e.indexOf(`//`) !== 0,
    r = t ?? (n ? rc : void 0);
  try {
    if (`canParse` in URL && !URL.canParse(e, r)) return;
    let t = new URL(e, r);
    return n
      ? { isRelative: n, pathname: t.pathname, search: t.search, hash: t.hash }
      : t;
  } catch {}
}
function ec(e) {
  if (Qs(e)) return e.pathname;
  let t = new URL(e);
  return (
    (t.search = ``),
    (t.hash = ``),
    [`80`, `443`].includes(t.port) && (t.port = ``),
    (t.password &&= `%filtered%`),
    (t.username &&= `%filtered%`),
    t.toString()
  );
}
function tc(e) {
  if (!e) return {};
  let t = e.match(
    /^(([^:/?#]+):)?(\/\/([^/?#]*))?([^?#]*)(\?([^#]*))?(#(.*))?$/,
  );
  if (!t) return {};
  let n = t[6] || ``,
    r = t[8] || ``;
  return {
    host: t[4],
    path: t[5],
    protocol: t[2],
    search: n,
    hash: r,
    relative: t[5] + n + r,
  };
}
function nc(e) {
  return e.split(/[?#]/, 1)[0];
}
var rc,
  ic = e(() => {
    rc = `thismessage:/`;
  });
function ac(e) {
  `aggregates` in e
    ? e.attrs?.ip_address === void 0 &&
      (e.attrs = { ...e.attrs, ip_address: `{{auto}}` })
    : e.ipAddress === void 0 && (e.ipAddress = `{{auto}}`);
}
var oc = e(() => {});
function sc(e, t, n = [t], r = `npm`) {
  let i = e._metadata || {};
  ((i.sdk ||= {
    name: `sentry.javascript.${t}`,
    packages: n.map((e) => ({ name: `${r}:@sentry/${e}`, version: Ne })),
    version: Ne,
  }),
    (e._metadata = i));
}
var cc = e(() => {
  Pe();
});
function lc(e = {}) {
  let t = e.client || P();
  if (!Ao() || !t) return {};
  let n = Jn(Fe());
  if (n.getTraceData) return n.getTraceData(e);
  let r = e.scope || M(),
    i = e.span || B(),
    a = i ? Xr(i) : uc(r),
    o = xr(i ? Ei(i) : Ti(t, r));
  if (!Kr.test(a))
    return (
      S.warn(`Invalid sentry-trace data. Cannot generate trace data`),
      {}
    );
  let s = { "sentry-trace": a, baggage: o };
  if (e.propagateTraceparent) {
    let e = i ? Zr(i) : dc(r);
    e && (s.traceparent = e);
  }
  return s;
}
function uc(e) {
  let {
    traceId: t,
    sampled: n,
    propagationSpanId: r,
  } = e.getPropagationContext();
  return Ur(t, r, n);
}
function dc(e) {
  let {
    traceId: t,
    sampled: n,
    propagationSpanId: r,
  } = e.getPropagationContext();
  return Wr(t, r, n);
}
var fc = e(() => {
  (Yn(), x(), F(), Fo(), C(), V(), Oi(), Dr(), qr());
});
function pc(e, t) {
  let n = P(),
    r = N();
  if (!n) return;
  let { beforeBreadcrumb: i = null, maxBreadcrumbs: a = mc } = n.getOptions();
  if (a <= 0) return;
  let o = { timestamp: mn(), ...e },
    s = i ? Re(() => i(o, t)) : o;
  s !== null &&
    (n.emit && n.emit(`beforeAddBreadcrumb`, s, t), r.addBreadcrumb(s, a));
}
var mc,
  hc = e(() => {
    (F(), C(), bn(), (mc = 100));
  }),
  gc,
  _c,
  vc,
  yc,
  bc,
  xc = e(() => {
    (F(),
      Yo(),
      Zt(),
      (_c = `FunctionToString`),
      (vc = new WeakMap()),
      (yc = () => ({
        name: _c,
        setupOnce() {
          gc = Function.prototype.toString;
          try {
            Function.prototype.toString = function (...e) {
              let t = Kt(this),
                n = vc.has(P()) && t !== void 0 ? t : this;
              return gc.apply(n, e);
            };
          } catch {}
        },
        setup(e) {
          vc.set(e, !0);
        },
      })),
      (bc = qo(yc)));
  });
function Sc(e = {}, t = {}) {
  return {
    allowUrls: [...(e.allowUrls || []), ...(t.allowUrls || [])],
    denyUrls: [...(e.denyUrls || []), ...(t.denyUrls || [])],
    ignoreErrors: [
      ...(e.ignoreErrors || []),
      ...(t.ignoreErrors || []),
      ...(e.disableErrorDefaults ? [] : jc),
    ],
    ignoreTransactions: [
      ...(e.ignoreTransactions || []),
      ...(t.ignoreTransactions || []),
    ],
  };
}
function Cc(e, t) {
  if (!e.type) {
    if (wc(e, t.ignoreErrors))
      return (
        _ &&
          S.warn(
            `Event dropped due to being matched by \`ignoreErrors\` option.\nEvent: ${sn(e)}`,
          ),
        !0
      );
    if (Ac(e))
      return (
        _ &&
          S.warn(
            `Event dropped due to not having an error message, error type or stacktrace.\nEvent: ${sn(e)}`,
          ),
        !0
      );
    if (Ec(e, t.denyUrls))
      return (
        _ &&
          S.warn(
            `Event dropped due to being matched by \`denyUrls\` option.\nEvent: ${sn(e)}.\nUrl: ${kc(e)}`,
          ),
        !0
      );
    if (!Dc(e, t.allowUrls))
      return (
        _ &&
          S.warn(
            `Event dropped due to not being matched by \`allowUrls\` option.\nEvent: ${sn(e)}.\nUrl: ${kc(e)}`,
          ),
        !0
      );
  } else if (e.type === `transaction` && Tc(e, t.ignoreTransactions))
    return (
      _ &&
        S.warn(
          `Event dropped due to being matched by \`ignoreTransactions\` option.\nEvent: ${sn(e)}`,
        ),
      !0
    );
  return !1;
}
function wc(e, t) {
  return t?.length ? Cs(e).some((e) => tn(e, t)) : !1;
}
function Tc(e, t) {
  if (!t?.length) return !1;
  let n = e.transaction;
  return n ? tn(n, t) : !1;
}
function Ec(e, t) {
  if (!t?.length) return !1;
  let n = kc(e);
  return n ? tn(n, t) : !1;
}
function Dc(e, t) {
  if (!t?.length) return !0;
  let n = kc(e);
  return n ? tn(n, t) : !0;
}
function Oc(e = []) {
  for (let t = e.length - 1; t >= 0; t--) {
    let n = e[t];
    if (n && n.filename !== `<anonymous>` && n.filename !== `[native code]`)
      return n.filename || null;
  }
  return null;
}
function kc(e) {
  try {
    let t = [...(e.exception?.values ?? [])]
      .reverse()
      .find(
        (e) =>
          e.mechanism?.parent_id === void 0 && e.stacktrace?.frames?.length,
      )?.stacktrace?.frames;
    return t ? Oc(t) : null;
  } catch {
    return (_ && S.error(`Cannot extract url for event ${sn(e)}`), null);
  }
}
function Ac(e) {
  return e.exception?.values?.length
    ? !e.message &&
        !e.exception.values.some(
          (e) => e.stacktrace || (e.type && e.type !== `Error`) || e.value,
        )
    : !1;
}
var jc,
  Mc,
  Nc,
  Pc,
  Fc = e(() => {
    (v(),
      Yo(),
      C(),
      ws(),
      pn(),
      nn(),
      (jc = [
        /^Script error\.?$/,
        /^Javascript error: Script error\.? on line 0$/,
        /^ResizeObserver loop completed with undelivered notifications.$/,
        /^Cannot redefine property: googletag$/,
        /^Can't find variable: gmo$/,
        /^undefined is not an object \(evaluating 'a\.[A-Z]'\)$/,
        `can't redefine non-configurable property "solana"`,
        `vv().getRestrictions is not a function. (In 'vv().getRestrictions(1,a)', 'vv().getRestrictions' is undefined)`,
        `Can't find variable: _AutofillCallbackHandler`,
        /^Non-Error promise rejection captured with value: Object Not Found Matching Id:\d+, MethodName:simulateEvent, ParamCount:\d+$/,
        /^Java exception was raised during method invocation$/,
      ]),
      (Mc = `EventFilters`),
      (Nc = qo((e = {}) => {
        let t;
        return {
          name: Mc,
          setup(n) {
            t = Sc(e, n.getOptions());
          },
          processEvent(n, r, i) {
            return ((t ||= Sc(e, i.getOptions())), Cc(n, t) ? null : n);
          },
        };
      })),
      (Pc = qo((e = {}) => ({ ...Nc(e), name: `InboundFilters` }))));
  });
function Ic(e, t, n, r, i, a) {
  if (!i.exception?.values || !a || !Ft(a.originalException, Error)) return;
  let o =
    i.exception.values.length > 0
      ? i.exception.values[i.exception.values.length - 1]
      : void 0;
  o &&
    (i.exception.values = Lc(
      e,
      t,
      r,
      a.originalException,
      n,
      i.exception.values,
      o,
      0,
    ));
}
function Lc(e, t, n, r, i, a, o, s) {
  if (a.length >= n + 1) return a;
  let c = [...a];
  if (Ft(r[i], Error)) {
    Rc(o, s);
    let a = e(t, r[i]),
      l = c.length;
    (zc(a, i, l, s), (c = Lc(e, t, n, r[i], i, [a, ...c], a, l)));
  }
  return (
    Array.isArray(r.errors) &&
      r.errors.forEach((r, a) => {
        if (Ft(r, Error)) {
          Rc(o, s);
          let l = e(t, r),
            u = c.length;
          (zc(l, `errors[${a}]`, u, s),
            (c = Lc(e, t, n, r, i, [l, ...c], l, u)));
        }
      }),
    c
  );
}
function Rc(e, t) {
  e.mechanism = {
    handled: !0,
    type: `auto.core.linked_errors`,
    ...e.mechanism,
    ...(e.type === `AggregateError` && { is_exception_group: !0 }),
    exception_id: t,
  };
}
function zc(e, t, n, r) {
  e.mechanism = {
    handled: !0,
    ...e.mechanism,
    type: `chained`,
    source: t,
    exception_id: n,
    parent_id: r,
  };
}
var Bc = e(() => {
  T();
});
function Vc(e) {
  let t = `console`;
  (ct(t, e), lt(t, Hc));
}
function Hc() {
  `console` in y &&
    qe.forEach(function (e) {
      e in y.console &&
        D(y.console, e, function (t) {
          return (
            (Ye[e] = t),
            function (...t) {
              (w(`console`, { args: t, level: e }), Ye[e]?.apply(y.console, t));
            }
          );
        });
    });
}
var Uc = e(() => {
  (C(), Zt(), b(), ft());
});
function Wc(e) {
  return e === `warn`
    ? `warning`
    : [`fatal`, `error`, `warning`, `log`, `info`, `debug`].includes(e)
      ? e
      : `log`;
}
var Gc = e(() => {});
function Kc(e, t) {
  return t ? !!(qc(e, t) || Jc(e, t)) : !1;
}
function qc(e, t) {
  let n = e.message,
    r = t.message;
  return !(
    (!n && !r) ||
    (n && !r) ||
    (!n && r) ||
    n !== r ||
    !Xc(e, t) ||
    !Yc(e, t)
  );
}
function Jc(e, t) {
  let n = Zc(t),
    r = Zc(e);
  return !(
    !n ||
    !r ||
    n.type !== r.type ||
    n.value !== r.value ||
    !Xc(e, t) ||
    !Yc(e, t)
  );
}
function Yc(e, t) {
  let n = tt(e),
    r = tt(t);
  if (!n && !r) return !0;
  if ((n && !r) || (!n && r) || ((n = n), (r = r), r.length !== n.length))
    return !1;
  for (let e = 0; e < r.length; e++) {
    let t = r[e],
      i = n[e];
    if (
      t.filename !== i.filename ||
      t.lineno !== i.lineno ||
      t.colno !== i.colno ||
      t.function !== i.function
    )
      return !1;
  }
  return !0;
}
function Xc(e, t) {
  let n = e.fingerprint,
    r = t.fingerprint;
  if (!n && !r) return !0;
  if ((n && !r) || (!n && r)) return !1;
  ((n = n), (r = r));
  try {
    return n.join(``) === r.join(``);
  } catch {
    return !1;
  }
}
function Zc(e) {
  return e.exception?.values?.[0];
}
var Qc,
  $c,
  el,
  tl = e(() => {
    (v(),
      Yo(),
      C(),
      st(),
      (Qc = `Dedupe`),
      ($c = () => {
        let e;
        return {
          name: Qc,
          processEvent(t) {
            if (t.type) return t;
            try {
              if (Kc(t, e))
                return (
                  _ &&
                    S.warn(
                      `Event dropped due to being a duplicate of previously captured event.`,
                    ),
                  null
                );
            } catch {}
            return (e = t);
          },
        };
      }),
      (el = qo($c)));
  });
function nl(e, t, n, r, i) {
  if (!e.fetchData) return;
  let { method: a, url: o } = e.fetchData,
    s = H() && t(o);
  if (e.endTimestamp && s) {
    let t = e.fetchData.__span;
    if (!t) return;
    let n = r[t];
    n && (al(n, e), rl(n, e, i), delete r[t]);
    return;
  }
  let { spanOrigin: c = `auto.http.browser`, propagateTraceparent: l = !1 } =
      typeof i == `object` ? i : { spanOrigin: i },
    u = !!B(),
    d = s && u ? Ca(cl(o, a, c)) : new ki();
  if (
    ((e.fetchData.__span = d.spanContext().spanId),
    (r[d.spanContext().spanId] = d),
    n(e.fetchData.url))
  ) {
    let t = e.args[0],
      n = e.args[1] || {},
      r = il(t, n, H() && u ? d : void 0, l);
    r && ((e.args[1] = n), (n.headers = r));
  }
  let f = P();
  if (f) {
    let t = {
      input: e.args,
      response: e.response,
      startTimestamp: e.startTimestamp,
      endTimestamp: e.endTimestamp,
    };
    f.emit(`beforeOutgoingRequestSpan`, d, t);
  }
  return d;
}
function rl(e, t, n) {
  (typeof n == `object` && n ? n.onRequestSpanEnd : void 0)?.(e, {
    headers: t.response?.headers,
    error: t.error,
  });
}
function il(e, t, n, r) {
  let i = lc({ span: n, propagateTraceparent: r }),
    a = i[`sentry-trace`],
    o = i.baggage,
    s = i.traceparent;
  if (!a) return;
  let c = t.headers || (Lt(e) ? e.headers : void 0);
  if (!c) return { ...i };
  if (sl(c)) {
    let e = new Headers(c);
    if (
      (e.get(`sentry-trace`) || e.set(`sentry-trace`, a),
      r && s && !e.get(`traceparent`) && e.set(`traceparent`, s),
      o)
    ) {
      let t = e.get(`baggage`);
      t ? ol(t) || e.set(`baggage`, `${t},${o}`) : e.set(`baggage`, o);
    }
    return e;
  } else if (Array.isArray(c)) {
    let e = [...c];
    (c.find((e) => e[0] === `sentry-trace`) || e.push([`sentry-trace`, a]),
      r &&
        s &&
        !c.find((e) => e[0] === `traceparent`) &&
        e.push([`traceparent`, s]));
    let t = c.find((e) => e[0] === `baggage` && ol(e[1]));
    return (o && !t && e.push([`baggage`, o]), e);
  } else {
    let e = `sentry-trace` in c ? c[`sentry-trace`] : void 0,
      t = `traceparent` in c ? c.traceparent : void 0,
      n = `baggage` in c ? c.baggage : void 0,
      i = n ? (Array.isArray(n) ? [...n] : [n]) : [],
      l = n && (Array.isArray(n) ? n.find((e) => ol(e)) : ol(n));
    o && !l && i.push(o);
    let u = {
      ...c,
      "sentry-trace": e ?? a,
      baggage: i.length > 0 ? i.join(`,`) : void 0,
    };
    return (r && s && !t && (u.traceparent = s), u);
  }
}
function al(e, t) {
  if (t.response) {
    dr(e, t.response.status);
    let n = t.response?.headers?.get(`content-length`);
    if (n) {
      let t = parseInt(n);
      t > 0 && e.setAttribute(`http.response_content_length`, t);
    }
  } else t.error && e.setStatus({ code: 2, message: `internal_error` });
  e.end();
}
function ol(e) {
  return e.split(`,`).some((e) => e.trim().startsWith(Tr));
}
function sl(e) {
  return typeof Headers < `u` && Ft(e, Headers);
}
function cl(e, t, n) {
  let r = $s(e);
  return { name: r ? `${t} ${ec(r)}` : t, attributes: ll(e, r, t, n) };
}
function ll(e, t, n, r) {
  let i = {
    url: e,
    type: `fetch`,
    "http.method": n,
    [L]: r,
    [tr]: `http.client`,
  };
  return (
    t &&
      (Qs(t) || ((i[`http.url`] = t.href), (i[`server.address`] = t.host)),
      t.search && (i[`http.query`] = t.search),
      t.hash && (i[`http.fragment`] = t.hash)),
    i
  );
}
var ul = e(() => {
  (F(), lr(), V(), fr(), T(), hi(), Dr(), Ai(), Na(), fc(), ic());
});
function dl(e) {
  if (e !== void 0) {
    if (e >= 400 && e < 500) return `warning`;
    if (e >= 500) return `error`;
  }
}
var fl = e(() => {});
function pl() {
  return `history` in _l && !!_l.history;
}
function ml() {
  if (!(`fetch` in _l)) return !1;
  try {
    return (new Headers(), new Request(`data:,`), new Response(), !0);
  } catch {
    return !1;
  }
}
function hl(e) {
  return (
    e && /^function\s+\w+\(\)\s+\{\s+\[native code\]\s+\}$/.test(e.toString())
  );
}
function gl() {
  if (typeof EdgeRuntime == `string`) return !0;
  if (!ml()) return !1;
  if (hl(_l.fetch)) return !0;
  let e = !1,
    t = _l.document;
  if (t && typeof t.createElement == `function`)
    try {
      let n = t.createElement(`iframe`);
      ((n.hidden = !0),
        t.head.appendChild(n),
        n.contentWindow?.fetch && (e = hl(n.contentWindow.fetch)),
        t.head.removeChild(n));
    } catch (e) {
      _ &&
        S.warn(
          `Could not create sandbox iframe for pure fetch check, bailing to window.fetch: `,
          e,
        );
    }
  return e;
}
var _l,
  vl = e(() => {
    (v(), C(), b(), (_l = y));
  });
function yl(e, t) {
  let n = `fetch`;
  (ct(n, e), lt(n, () => xl(void 0, t)));
}
function bl(e) {
  let t = `fetch-body-resolved`;
  (ct(t, e), lt(t, () => xl(Cl)));
}
function xl(e, t = !1) {
  (t && !gl()) ||
    D(y, `fetch`, function (t) {
      return function (...n) {
        let r = Error(),
          { method: i, url: a } = El(n),
          o = {
            args: n,
            fetchData: { method: i, url: a },
            startTimestamp: A() * 1e3,
            virtualError: r,
            headers: Dl(n),
          };
        return (
          e || w(`fetch`, { ...o }),
          t.apply(y, n).then(
            async (t) => (
              e
                ? e(t)
                : w(`fetch`, { ...o, endTimestamp: A() * 1e3, response: t }),
              t
            ),
            (e) => {
              if (
                (w(`fetch`, { ...o, endTimestamp: A() * 1e3, error: e }),
                xt(e) &&
                  e.stack === void 0 &&
                  ((e.stack = r.stack), O(e, `framesToPop`, 1)),
                e instanceof TypeError &&
                  (e.message === `Failed to fetch` ||
                    e.message === `Load failed` ||
                    e.message ===
                      `NetworkError when attempting to fetch resource.`))
              )
                try {
                  let t = new URL(o.fetchData.url);
                  e.message = `${e.message} (${t.host})`;
                } catch {}
              throw e;
            },
          )
        );
      };
    });
}
async function Sl(e, t) {
  if (e?.body) {
    let n = e.body,
      r = n.getReader(),
      i = setTimeout(() => {
        n.cancel().then(null, () => {});
      }, 90 * 1e3),
      a = !0;
    for (; a; ) {
      let e;
      try {
        e = setTimeout(() => {
          n.cancel().then(null, () => {});
        }, 5e3);
        let { done: i } = await r.read();
        (clearTimeout(e), i && (t(), (a = !1)));
      } catch {
        a = !1;
      } finally {
        clearTimeout(e);
      }
    }
    (clearTimeout(i), r.releaseLock(), n.cancel().then(null, () => {}));
  }
}
function Cl(e) {
  let t;
  try {
    t = e.clone();
  } catch {
    return;
  }
  Sl(t, () => {
    w(`fetch-body-resolved`, { endTimestamp: A() * 1e3, response: e });
  });
}
function wl(e, t) {
  return !!e && typeof e == `object` && !!e[t];
}
function Tl(e) {
  return typeof e == `string`
    ? e
    : e
      ? wl(e, `url`)
        ? e.url
        : e.toString
          ? e.toString()
          : ``
      : ``;
}
function El(e) {
  if (e.length === 0) return { method: `GET`, url: `` };
  if (e.length === 2) {
    let [t, n] = e;
    return {
      url: Tl(t),
      method: wl(n, `method`) ? String(n.method).toUpperCase() : `GET`,
    };
  }
  let t = e[0];
  return {
    url: Tl(t),
    method: wl(t, `method`) ? String(t.method).toUpperCase() : `GET`,
  };
}
function Dl(e) {
  let [t, n] = e;
  try {
    if (typeof n == `object` && n && `headers` in n && n.headers)
      return new Headers(n.headers);
    if (Lt(t)) return new Headers(t.headers);
  } catch {}
}
var Ol = e(() => {
  (T(), Zt(), vl(), bn(), b(), ft());
});
function kl() {
  return typeof __SENTRY_BROWSER_BUNDLE__ < `u` && !!__SENTRY_BROWSER_BUNDLE__;
}
function Al() {
  return `npm`;
}
var jl = e(() => {});
function Ml() {
  return (
    !kl() &&
    Object.prototype.toString.call(typeof process < `u` ? process : 0) ===
      `[object process]`
  );
}
var Nl = e(() => {
  jl();
});
function Pl() {
  return typeof window < `u` && (!Ml() || Fl());
}
function Fl() {
  return y.process?.type === `renderer`;
}
var Il = e(() => {
  (Nl(), b());
});
function Ll(e, t = !1) {
  return (
    !(
      t ||
      (e &&
        !e.startsWith(`/`) &&
        !e.match(/^[A-Z]:/) &&
        !e.startsWith(`.`) &&
        !e.match(/^[a-zA-Z]([a-zA-Z0-9.\-+])*:\/\//))
    ) &&
    e !== void 0 &&
    !e.includes(`node_modules/`)
  );
}
function Rl(e) {
  let t = /^\s*[-]{4,}$/,
    n = /at (?:async )?(?:(.+?)\s+\()?(?:(.+):(\d+):(\d+)?|([^)]+))\)?/,
    r = /at (?:async )?(.+?) \(data:(.*?),/;
  return (i) => {
    let a = i.match(r);
    if (a) return { filename: `<data:${a[2]}>`, function: a[1] };
    let o = i.match(n);
    if (o) {
      let t, n, r, i, a;
      if (o[1]) {
        r = o[1];
        let e = r.lastIndexOf(`.`);
        if ((r[e - 1] === `.` && e--, e > 0)) {
          ((t = r.slice(0, e)), (n = r.slice(e + 1)));
          let i = t.indexOf(`.Module`);
          i > 0 && ((r = r.slice(i + 1)), (t = t.slice(0, i)));
        }
        i = void 0;
      }
      (n && ((i = t), (a = n)),
        n === `<anonymous>` && ((a = void 0), (r = void 0)),
        r === void 0 && ((a ||= `?`), (r = i ? `${i}.${a}` : a)));
      let s = o[2]?.startsWith(`file://`) ? o[2].slice(7) : o[2],
        c = o[5] === `native`;
      return (
        s?.match(/\/[A-Z]:/) && (s = s.slice(1)),
        !s && o[5] && !c && (s = o[5]),
        {
          filename: s ? decodeURI(s) : void 0,
          module: e ? e(s) : void 0,
          function: r,
          lineno: Bl(o[3]),
          colno: Bl(o[4]),
          in_app: Ll(s || ``, c),
        }
      );
    }
    if (i.match(t)) return { filename: i };
  };
}
function zl(e) {
  return [90, Rl(e)];
}
function Bl(e) {
  return parseInt(e || ``, 10) || void 0;
}
var Vl = e(() => {
    st();
  }),
  U = e(() => {
    (mi(),
      Va(),
      Ai(),
      fr(),
      Na(),
      Oi(),
      la(),
      lr(),
      Fo(),
      F(),
      Js(),
      Zs(),
      bs(),
      Yo(),
      oo(),
      hi(),
      oc(),
      V(),
      cc(),
      fc(),
      hc(),
      xc(),
      Fc(),
      tl(),
      ul(),
      ns(),
      ls(),
      Bc(),
      fl(),
      Wt(),
      b(),
      Uc(),
      Ol(),
      gt(),
      bt(),
      ft(),
      T(),
      Il(),
      C(),
      pn(),
      zi(),
      Zt(),
      fs(),
      Gc(),
      st(),
      Vl(),
      nn(),
      vl(),
      Ja(),
      bn(),
      qr(),
      jl(),
      ic(),
      An());
  });
function Hl(e) {
  return {
    createUrl: (t) => `${e}://${t}/sentry_key`,
    urlMatches: function (e, t) {
      return e.startsWith(this.createUrl(t));
    },
    createKey: (t) => `${e}.${t}`,
    namespace: e,
  };
}
var Ul,
  Wl,
  Gl = e(() => {
    ((function (e) {
      ((e[(e.Classic = 1)] = `Classic`),
        (e[(e.Protocol = 2)] = `Protocol`),
        (e[(e.Both = 3)] = `Both`));
    })((Ul ||= {})),
      (Wl = `sentry-electron-renderer-id`));
  });
function Kl(e) {
  let t = Hl(e);
  if (window.__SENTRY_IPC__?.[t.namespace])
    return window.__SENTRY_IPC__[t.namespace];
  {
    S.log(
      `IPC was not configured in preload script, falling back to custom protocol and fetch`,
    );
    let e = (window.__SENTRY_RENDERER_ID__ = k()),
      n = { [Wl]: e };
    return {
      sendRendererStart: () => {
        fetch(t.createUrl(`start`), {
          method: `POST`,
          body: ``,
          headers: n,
        }).catch(() => {
          console.error(`Sentry SDK failed to establish connection with the Electron main process.
  - Ensure you have initialized the SDK in the main process
  - If your renderers use custom sessions, be sure to set 'getSessions' in the main process options
  - If you are bundling your main process code and using Electron < v5, you'll need to manually configure a preload script`);
        });
      },
      sendScope: (e) => {
        fetch(t.createUrl(`scope`), {
          method: `POST`,
          body: e,
          headers: n,
        }).catch(() => {});
      },
      sendEnvelope: (e) => {
        fetch(t.createUrl(`envelope`), {
          method: `POST`,
          body: e,
          headers: n,
        }).catch(() => {});
      },
      sendStatus: (e) => {
        fetch(t.createUrl(`status`), {
          method: `POST`,
          body: JSON.stringify({ status: e }),
          headers: n,
        }).catch(() => {});
      },
      sendStructuredLog: (e) => {
        fetch(t.createUrl(`structured-log`), {
          method: `POST`,
          body: JSON.stringify(e),
          headers: n,
        }).catch(() => {});
      },
      sendMetric: (e) => {
        fetch(t.createUrl(`metric`), {
          method: `POST`,
          body: JSON.stringify(e),
          headers: n,
        }).catch(() => {});
      },
    };
  }
}
function ql(e = P()) {
  if (!e)
    throw Error(
      `Could not find client, make sure to call Sentry.init before getIPC`,
    );
  Jl ||= new WeakMap();
  let t = Jl.get(e);
  if (t) return t;
  let n = e.getOptions().ipcNamespace,
    r = Kl(n);
  return (Jl.set(e, r), r.sendRendererStart(), r);
}
var Jl,
  Yl = e(() => {
    (U(), Gl());
  }),
  Xl = e(() => {
    Yl();
  }),
  Zl = e(() => {
    Yl();
  });
function Ql() {
  return nu > 0;
}
function $l() {
  (nu++,
    setTimeout(() => {
      nu--;
    }));
}
function eu(e, t = {}) {
  function n(e) {
    return typeof e == `function`;
  }
  if (!n(e)) return e;
  try {
    let t = e.__sentry_wrapped__;
    if (t) return typeof t == `function` ? t : e;
    if (Kt(e)) return e;
  } catch {
    return e;
  }
  let r = function (...n) {
    try {
      let r = n.map((e) => eu(e, t));
      return e.apply(this, r);
    } catch (e) {
      throw (
        $l(),
        Zn((r) => {
          (r.addEventProcessor(
            (e) => (
              t.mechanism && (cn(e, void 0, void 0), ln(e, t.mechanism)),
              (e.extra = { ...e.extra, arguments: n }),
              e
            ),
          ),
            Eo(e));
        }),
        e
      );
    }
  };
  try {
    for (let t in e)
      Object.prototype.hasOwnProperty.call(e, t) && (r[t] = e[t]);
  } catch {}
  (Gt(r, e), O(e, `__sentry_wrapped__`, r));
  try {
    Object.getOwnPropertyDescriptor(r, `name`).configurable &&
      Object.defineProperty(r, `name`, {
        get() {
          return e.name;
        },
      });
  } catch {}
  return r;
}
function tu() {
  let e = Bt(),
    { referrer: t } = W.document || {},
    { userAgent: n } = W.navigator || {};
  return {
    url: e,
    headers: { ...(t && { Referer: t }), ...(n && { "User-Agent": n }) },
  };
}
var W,
  nu,
  G = e(() => {
    (U(), (W = y), (nu = 0));
  });
function ru(e, t) {
  let n = ou(e, t),
    r = { type: uu(t), value: du(t) };
  return (
    n.length && (r.stacktrace = { frames: n }),
    r.type === void 0 &&
      r.value === `` &&
      (r.value = `Unrecoverable error caught`),
    r
  );
}
function iu(e, t, n, r) {
  let i = P()?.getOptions().normalizeDepth,
    a = vu(t),
    o = { __serialized__: Mi(t, i) };
  if (a) return { exception: { values: [ru(e, a)] }, extra: o };
  let s = {
    exception: {
      values: [
        {
          type: At(t) ? t.constructor.name : r ? `UnhandledRejection` : `Error`,
          value: gu(t, { isUnhandledRejection: r }),
        },
      ],
    },
    extra: o,
  };
  if (n) {
    let t = ou(e, n);
    t.length && (s.exception.values[0].stacktrace = { frames: t });
  }
  return s;
}
function au(e, t) {
  return { exception: { values: [ru(e, t)] } };
}
function ou(e, t) {
  let n = t.stacktrace || t.stack || ``,
    r = su(t),
    i = cu(t);
  try {
    return e(n, r, i);
  } catch {}
  return [];
}
function su(e) {
  return e && yu.test(e.message) ? 1 : 0;
}
function cu(e) {
  return typeof e.framesToPop == `number` ? e.framesToPop : 0;
}
function lu(e) {
  return typeof WebAssembly < `u` && WebAssembly.Exception !== void 0
    ? e instanceof WebAssembly.Exception
    : !1;
}
function uu(e) {
  let t = e?.name;
  return !t && lu(e)
    ? e.message && Array.isArray(e.message) && e.message.length == 2
      ? e.message[0]
      : `WebAssembly.Exception`
    : t;
}
function du(e) {
  let t = e?.message;
  return lu(e)
    ? Array.isArray(e.message) && e.message.length == 2
      ? e.message[1]
      : `wasm exception`
    : t
      ? t.error && typeof t.error.message == `string`
        ? t.error.message
        : t
      : `No error message`;
}
function fu(e, t, n, r) {
  let i = mu(e, t, n?.syntheticException || void 0, r);
  return (
    ln(i),
    (i.level = `error`),
    n?.event_id && (i.event_id = n.event_id),
    Ha(i)
  );
}
function pu(e, t, n = `info`, r, i) {
  let a = hu(e, t, r?.syntheticException || void 0, i);
  return ((a.level = n), r?.event_id && (a.event_id = r.event_id), Ha(a));
}
function mu(e, t, n, r, i) {
  let a;
  if (Ct(t) && t.error) return au(e, t.error);
  if (wt(t) || Tt(t)) {
    let i = t;
    if (`stack` in t) a = au(e, t);
    else {
      let t = i.name || (wt(i) ? `DOMError` : `DOMException`),
        o = i.message ? `${t}: ${i.message}` : t;
      ((a = hu(e, o, n, r)), cn(a, o));
    }
    return (
      `code` in i && (a.tags = { ...a.tags, "DOMException.code": `${i.code}` }),
      a
    );
  }
  return xt(t)
    ? au(e, t)
    : kt(t) || At(t)
      ? ((a = iu(e, t, n, i)), ln(a, { synthetic: !0 }), a)
      : ((a = hu(e, t, n, r)),
        cn(a, `${t}`, void 0),
        ln(a, { synthetic: !0 }),
        a);
}
function hu(e, t, n, r) {
  let i = {};
  if (r && n) {
    let r = ou(e, n);
    (r.length &&
      (i.exception = { values: [{ value: t, stacktrace: { frames: r } }] }),
      ln(i, { synthetic: !0 }));
  }
  if (Dt(t)) {
    let { __sentry_template_string__: e, __sentry_template_values__: n } = t;
    return ((i.logentry = { message: e, params: n }), i);
  }
  return ((i.message = t), i);
}
function gu(e, { isUnhandledRejection: t }) {
  let n = Xt(e),
    r = t ? `promise rejection` : `exception`;
  return Ct(e)
    ? `Event \`ErrorEvent\` captured as ${r} with message \`${e.message}\``
    : At(e)
      ? `Event \`${_u(e)}\` (type=${e.type}) captured as ${r}`
      : `Object captured as ${r} with keys: ${n}`;
}
function _u(e) {
  try {
    let t = Object.getPrototypeOf(e);
    return t ? t.constructor.name : void 0;
  } catch {}
}
function vu(e) {
  for (let t in e)
    if (Object.prototype.hasOwnProperty.call(e, t)) {
      let n = e[t];
      if (n instanceof Error) return n;
    }
}
var yu,
  bu = e(() => {
    (U(), (yu = /Minified React error #\d+;/i));
  });
function xu(e) {
  return {
    release:
      typeof __SENTRY_RELEASE__ == `string`
        ? __SENTRY_RELEASE__
        : W.SENTRY_RELEASE?.id,
    sendClientReports: !0,
    parentSpanIsAlwaysRootSpan: !0,
    ...e,
  };
}
var Su,
  Cu = e(() => {
    (U(),
      bu(),
      G(),
      (Su = class extends qs {
        constructor(e) {
          let t = xu(e);
          (sc(t, `browser`, [`browser`], W.SENTRY_SDK_SOURCE || Al()),
            t._metadata?.sdk &&
              (t._metadata.sdk.settings = {
                infer_ip: t.sendDefaultPii ? `auto` : `never`,
                ...t._metadata.sdk.settings,
              }),
            super(t));
          let {
              sendDefaultPii: n,
              sendClientReports: r,
              enableLogs: i,
              _experiments: a,
              enableMetrics: o,
            } = this._options,
            s = o ?? a?.enableMetrics ?? !0;
          (W.document &&
            (r || i || s) &&
            W.document.addEventListener(`visibilitychange`, () => {
              W.document.visibilityState === `hidden` &&
                (r && this._flushOutcomes(), i && $o(this), s && os(this));
            }),
            n && this.on(`beforeSendSession`, ac));
        }
        eventFromException(e, t) {
          return fu(
            this._options.stackParser,
            e,
            t,
            this._options.attachStacktrace,
          );
        }
        eventFromMessage(e, t = `info`, n) {
          return pu(
            this._options.stackParser,
            e,
            t,
            n,
            this._options.attachStacktrace,
          );
        }
        _prepareEvent(e, t, n, r) {
          return (
            (e.platform = e.platform || `javascript`),
            super._prepareEvent(e, t, n, r)
          );
        }
      }));
  }),
  wu,
  Tu = e(() => {
    wu = typeof __SENTRY_DEBUG__ > `u` || __SENTRY_DEBUG__;
  }),
  K,
  q = e(() => {
    (U(), (K = y));
  }),
  Eu,
  Du,
  Ou = e(() => {
    ((Eu = (e, t) =>
      e > t[1] ? `poor` : e > t[0] ? `needs-improvement` : `good`),
      (Du = (e, t, n, r) => {
        let i, a;
        return (o) => {
          t.value >= 0 &&
            (o || r) &&
            ((a = t.value - (i ?? 0)),
            (a || i === void 0) &&
              ((i = t.value),
              (t.delta = a),
              (t.rating = Eu(t.value, n)),
              e(t)));
        };
      }));
  }),
  ku,
  Au = e(() => {
    (q(),
      (ku = (e = !0) => {
        let t = K.performance?.getEntriesByType?.(`navigation`)[0];
        if (
          !e ||
          (t && t.responseStart > 0 && t.responseStart < performance.now())
        )
          return t;
      }));
  }),
  ju,
  Mu = e(() => {
    (Au(), (ju = () => ku()?.activationStart ?? 0));
  });
function Nu(e, t, n) {
  K.document && K.addEventListener(e, t, n);
}
function Pu(e, t, n) {
  K.document && K.removeEventListener(e, t, n);
}
var Fu = e(() => {
  q();
});
function Iu(e) {
  return e.type === `pagehide` || K.document?.visibilityState === `hidden`;
}
var Lu,
  Ru,
  zu,
  Bu,
  Vu,
  Hu = e(() => {
    (q(),
      Mu(),
      Fu(),
      (Lu = -1),
      (Ru = new Set()),
      (zu = () =>
        K.document?.visibilityState === `hidden` && !K.document?.prerendering
          ? 0
          : 1 / 0),
      (Bu = (e) => {
        if (Iu(e) && Lu > -1) {
          if (e.type === `visibilitychange` || e.type === `pagehide`)
            for (let e of Ru) e();
          isFinite(Lu) ||
            ((Lu = e.type === `visibilitychange` ? e.timeStamp : 0),
            Pu(`prerenderingchange`, Bu, !0));
        }
      }),
      (Vu = () => {
        if (K.document && Lu < 0) {
          let e = ju();
          ((Lu =
            (K.document.prerendering
              ? void 0
              : globalThis.performance
                  .getEntriesByType(`visibility-state`)
                  .filter((t) => t.name === `hidden` && t.startTime > e)[0]
                  ?.startTime) ?? zu()),
            Nu(`visibilitychange`, Bu, !0),
            Nu(`pagehide`, Bu, !0),
            Nu(`prerenderingchange`, Bu, !0));
        }
        return {
          get firstHiddenTime() {
            return Lu;
          },
          onHidden(e) {
            Ru.add(e);
          },
        };
      }));
  }),
  Uu,
  Wu = e(() => {
    Uu = () =>
      `v5-${Date.now()}-${Math.floor(Math.random() * 8999999999999) + 0xe8d4a51000}`;
  }),
  Gu,
  Ku = e(() => {
    (q(),
      Wu(),
      Mu(),
      Au(),
      (Gu = (e, t = -1) => {
        let n = ku(),
          r = `navigate`;
        return (
          n &&
            (K.document?.prerendering || ju() > 0
              ? (r = `prerender`)
              : K.document?.wasDiscarded
                ? (r = `restore`)
                : n.type && (r = n.type.replace(/_/g, `-`))),
          {
            name: e,
            value: t,
            rating: `good`,
            delta: 0,
            entries: [],
            id: Uu(),
            navigationType: r,
          }
        );
      }));
  });
function qu(e, t) {
  return (Ju.get(e) || Ju.set(e, new t()), Ju.get(e));
}
var Ju,
  Yu = e(() => {
    Ju = new WeakMap();
  }),
  Xu,
  Zu = e(() => {
    Xu = class e {
      constructor() {
        (e.prototype.__init.call(this), e.prototype.__init2.call(this));
      }
      __init() {
        this._sessionValue = 0;
      }
      __init2() {
        this._sessionEntries = [];
      }
      _processEntry(e) {
        if (e.hadRecentInput) return;
        let t = this._sessionEntries[0],
          n = this._sessionEntries[this._sessionEntries.length - 1];
        (this._sessionValue &&
        t &&
        n &&
        e.startTime - n.startTime < 1e3 &&
        e.startTime - t.startTime < 5e3
          ? ((this._sessionValue += e.value), this._sessionEntries.push(e))
          : ((this._sessionValue = e.value), (this._sessionEntries = [e])),
          this._onAfterProcessingUnexpectedShift?.(e));
      }
    };
  }),
  Qu,
  $u = e(() => {
    Qu = (e, t, n = {}) => {
      try {
        if (PerformanceObserver.supportedEntryTypes.includes(e)) {
          let r = new PerformanceObserver((e) => {
            Promise.resolve().then(() => {
              t(e.getEntries());
            });
          });
          return (r.observe({ type: e, buffered: !0, ...n }), r);
        }
      } catch {}
    };
  }),
  ed,
  td = e(() => {
    ed = (e) => {
      let t = !1;
      return () => {
        t ||= (e(), !0);
      };
    };
  }),
  nd,
  rd = e(() => {
    (q(),
      (nd = (e) => {
        K.document?.prerendering
          ? addEventListener(`prerenderingchange`, () => e(), !0)
          : e();
      }));
  }),
  id,
  ad,
  od = e(() => {
    (Ou(),
      Mu(),
      Hu(),
      Ku(),
      $u(),
      rd(),
      (id = [1800, 3e3]),
      (ad = (e, t = {}) => {
        nd(() => {
          let n = Vu(),
            r = Gu(`FCP`),
            i,
            a = Qu(`paint`, (e) => {
              for (let t of e)
                t.name === `first-contentful-paint` &&
                  (a.disconnect(),
                  t.startTime < n.firstHiddenTime &&
                    ((r.value = Math.max(t.startTime - ju(), 0)),
                    r.entries.push(t),
                    i(!0)));
            });
          a && (i = Du(e, r, id, t.reportAllChanges));
        });
      }));
  }),
  sd,
  cd,
  ld = e(() => {
    (q(),
      Ou(),
      Hu(),
      Ku(),
      Yu(),
      Zu(),
      $u(),
      td(),
      od(),
      (sd = [0.1, 0.25]),
      (cd = (e, t = {}) => {
        ad(
          ed(() => {
            let n = Gu(`CLS`, 0),
              r,
              i = Vu(),
              a = qu(t, Xu),
              o = (e) => {
                for (let t of e) a._processEntry(t);
                a._sessionValue > n.value &&
                  ((n.value = a._sessionValue),
                  (n.entries = a._sessionEntries),
                  r());
              },
              s = Qu(`layout-shift`, o);
            s &&
              ((r = Du(e, n, sd, t.reportAllChanges)),
              i.onHidden(() => {
                (o(s.takeRecords()), r(!0));
              }),
              K?.setTimeout?.(r));
          }),
        );
      }));
  }),
  ud,
  dd,
  fd,
  pd,
  md,
  hd,
  gd,
  _d = e(() => {
    ($u(),
      (ud = 0),
      (dd = 1 / 0),
      (fd = 0),
      (pd = (e) => {
        e.forEach((e) => {
          e.interactionId &&
            ((dd = Math.min(dd, e.interactionId)),
            (fd = Math.max(fd, e.interactionId)),
            (ud = fd ? (fd - dd) / 7 + 1 : 0));
        });
      }),
      (hd = () => (md ? ud : performance.interactionCount || 0)),
      (gd = () => {
        `interactionCount` in performance ||
          md ||
          (md = Qu(`event`, pd, {
            type: `event`,
            buffered: !0,
            durationThreshold: 0,
          }));
      }));
  }),
  vd,
  yd,
  bd,
  xd,
  Sd = e(() => {
    (_d(),
      (vd = 10),
      (yd = 0),
      (bd = () => hd() - yd),
      (xd = class e {
        constructor() {
          (e.prototype.__init.call(this), e.prototype.__init2.call(this));
        }
        __init() {
          this._longestInteractionList = [];
        }
        __init2() {
          this._longestInteractionMap = new Map();
        }
        _resetInteractions() {
          ((yd = hd()),
            (this._longestInteractionList.length = 0),
            this._longestInteractionMap.clear());
        }
        _estimateP98LongestInteraction() {
          let e = Math.min(
            this._longestInteractionList.length - 1,
            Math.floor(bd() / 50),
          );
          return this._longestInteractionList[e];
        }
        _processEntry(e) {
          if (
            (this._onBeforeProcessingEntry?.(e),
            !(e.interactionId || e.entryType === `first-input`))
          )
            return;
          let t = this._longestInteractionList.at(-1),
            n = this._longestInteractionMap.get(e.interactionId);
          if (
            n ||
            this._longestInteractionList.length < vd ||
            e.duration > t._latency
          ) {
            if (
              (n
                ? e.duration > n._latency
                  ? ((n.entries = [e]), (n._latency = e.duration))
                  : e.duration === n._latency &&
                    e.startTime === n.entries[0].startTime &&
                    n.entries.push(e)
                : ((n = {
                    id: e.interactionId,
                    entries: [e],
                    _latency: e.duration,
                  }),
                  this._longestInteractionMap.set(n.id, n),
                  this._longestInteractionList.push(n)),
              this._longestInteractionList.sort(
                (e, t) => t._latency - e._latency,
              ),
              this._longestInteractionList.length > vd)
            ) {
              let e = this._longestInteractionList.splice(vd);
              for (let t of e) this._longestInteractionMap.delete(t.id);
            }
            this._onAfterProcessingINPCandidate?.(n);
          }
        }
      }));
  }),
  Cd,
  wd = e(() => {
    (q(),
      Fu(),
      (Cd = (e) => {
        let t = (t) => {
          (t.type === `pagehide` || K.document?.visibilityState === `hidden`) &&
            e(t);
        };
        (Nu(`visibilitychange`, t, !0), Nu(`pagehide`, t, !0));
      }));
  }),
  Td,
  Ed = e(() => {
    (q(),
      Fu(),
      wd(),
      td(),
      (Td = (e) => {
        let t = K.requestIdleCallback || K.setTimeout;
        K.document?.visibilityState === `hidden`
          ? e()
          : ((e = ed(e)),
            Nu(`visibilitychange`, e, { once: !0, capture: !0 }),
            t(() => {
              (e(), Pu(`visibilitychange`, e, { capture: !0 }));
            }),
            Cd(e));
      }));
  }),
  Dd,
  Od,
  kd,
  Ad = e(() => {
    (Ou(),
      Hu(),
      Ku(),
      Yu(),
      Sd(),
      $u(),
      _d(),
      rd(),
      Ed(),
      (Dd = [200, 500]),
      (Od = 40),
      (kd = (e, t = {}) => {
        if (
          !(
            globalThis.PerformanceEventTiming &&
            `interactionId` in PerformanceEventTiming.prototype
          )
        )
          return;
        let n = Vu();
        nd(() => {
          gd();
          let r = Gu(`INP`),
            i,
            a = qu(t, xd),
            o = (e) => {
              Td(() => {
                for (let t of e) a._processEntry(t);
                let t = a._estimateP98LongestInteraction();
                t &&
                  t._latency !== r.value &&
                  ((r.value = t._latency), (r.entries = t.entries), i());
              });
            },
            s = Qu(`event`, o, {
              durationThreshold: t.durationThreshold ?? Od,
            });
          ((i = Du(e, r, Dd, t.reportAllChanges)),
            s &&
              (s.observe({ type: `first-input`, buffered: !0 }),
              n.onHidden(() => {
                (o(s.takeRecords()), i(!0));
              })));
        });
      }));
  }),
  jd,
  Md = e(() => {
    jd = class {
      _processEntry(e) {
        this._onBeforeProcessingEntry?.(e);
      }
    };
  }),
  Nd,
  Pd,
  Fd = e(() => {
    (Ou(),
      Mu(),
      Hu(),
      Fu(),
      Ku(),
      Yu(),
      Md(),
      $u(),
      td(),
      rd(),
      Ed(),
      (Nd = [2500, 4e3]),
      (Pd = (e, t = {}) => {
        nd(() => {
          let n = Vu(),
            r = Gu(`LCP`),
            i,
            a = qu(t, jd),
            o = (e) => {
              t.reportAllChanges || (e = e.slice(-1));
              for (let t of e)
                (a._processEntry(t),
                  t.startTime < n.firstHiddenTime &&
                    ((r.value = Math.max(t.startTime - ju(), 0)),
                    (r.entries = [t]),
                    i()));
            },
            s = Qu(`largest-contentful-paint`, o);
          if (s) {
            i = Du(e, r, Nd, t.reportAllChanges);
            let n = ed(() => {
                (o(s.takeRecords()), s.disconnect(), i(!0));
              }),
              a = (e) => {
                e.isTrusted && (Td(n), Pu(e.type, a, { capture: !0 }));
              };
            for (let e of [`keydown`, `click`, `visibilitychange`])
              Nu(e, a, { capture: !0 });
          }
        });
      }));
  }),
  Id,
  Ld,
  Rd,
  zd = e(() => {
    (q(),
      Ou(),
      Mu(),
      Au(),
      Ku(),
      rd(),
      (Id = [800, 1800]),
      (Ld = (e) => {
        K.document?.prerendering
          ? nd(() => Ld(e))
          : K.document?.readyState === `complete`
            ? setTimeout(e)
            : addEventListener(`load`, () => Ld(e), !0);
      }),
      (Rd = (e, t = {}) => {
        let n = Gu(`TTFB`),
          r = Du(e, n, Id, t.reportAllChanges);
        Ld(() => {
          let e = ku();
          e &&
            ((n.value = Math.max(e.responseStart - ju(), 0)),
            (n.entries = [e]),
            r(!0));
        });
      }));
  });
function Bd(e, t = !1) {
  return Xd(`cls`, e, Kd, rf, t);
}
function Vd(e, t = !1) {
  return Xd(`lcp`, e, qd, af, t);
}
function Hd(e) {
  return Xd(`ttfb`, e, Jd, of);
}
function Ud(e) {
  return Xd(`inp`, e, Yd, sf);
}
function Wd(e, t) {
  return (Qd(e, t), nf[e] || (Zd(e), (nf[e] = !0)), $d(e, t));
}
function Gd(e, t) {
  let n = tf[e];
  if (n?.length)
    for (let r of n)
      try {
        r(t);
      } catch (t) {
        wu &&
          S.error(
            `Error while triggering instrumentation handler.\nType: ${e}\nName: ${et(r)}\nError:`,
            t,
          );
      }
}
function Kd() {
  return cd(
    (e) => {
      (Gd(`cls`, { metric: e }), (rf = e));
    },
    { reportAllChanges: !0 },
  );
}
function qd() {
  return Pd(
    (e) => {
      (Gd(`lcp`, { metric: e }), (af = e));
    },
    { reportAllChanges: !0 },
  );
}
function Jd() {
  return Rd((e) => {
    (Gd(`ttfb`, { metric: e }), (of = e));
  });
}
function Yd() {
  return kd((e) => {
    (Gd(`inp`, { metric: e }), (sf = e));
  });
}
function Xd(e, t, n, r, i = !1) {
  Qd(e, t);
  let a;
  return (
    nf[e] || ((a = n()), (nf[e] = !0)),
    r && t({ metric: r }),
    $d(e, t, i ? a : void 0)
  );
}
function Zd(e) {
  let t = {};
  (e === `event` && (t.durationThreshold = 0),
    Qu(
      e,
      (t) => {
        Gd(e, { entries: t });
      },
      t,
    ));
}
function Qd(e, t) {
  ((tf[e] = tf[e] || []), tf[e].push(t));
}
function $d(e, t, n) {
  return () => {
    n && n();
    let r = tf[e];
    if (!r) return;
    let i = r.indexOf(t);
    i !== -1 && r.splice(i, 1);
  };
}
function ef(e) {
  return `duration` in e;
}
var tf,
  nf,
  rf,
  af,
  of,
  sf,
  cf = e(() => {
    (U(), Tu(), ld(), Ad(), Fd(), $u(), zd(), (tf = {}), (nf = {}));
  });
function lf(e) {
  return typeof e == `number` && isFinite(e);
}
function uf(e, t, n, { ...r }) {
  let i = R(e).start_timestamp;
  return (
    i &&
      i > t &&
      typeof e.updateStartTime == `function` &&
      e.updateStartTime(t),
    wa(e, () => {
      let e = Ca({ startTime: t, ...r });
      return (e && e.end(n), e);
    })
  );
}
function df(e) {
  let t = P();
  if (!t) return;
  let { name: n, transaction: r, attributes: i, startTime: a } = e,
    { release: o, environment: s, sendDefaultPii: c } = t.getOptions(),
    l = t.getIntegrationByName(`Replay`)?.getReplayId(),
    u = M(),
    d = u.getUser(),
    f = d === void 0 ? void 0 : d.email || d.id || d.ip_address,
    p;
  try {
    p = u.getScopeData().contexts.profile.profile_id;
  } catch {}
  return Ca({
    name: n,
    attributes: {
      release: o,
      environment: s,
      user: f || void 0,
      profile_id: p || void 0,
      replay_id: l || void 0,
      transaction: r,
      "user_agent.original": K.navigator?.userAgent,
      "client.address": c ? `{{auto}}` : void 0,
      ...i,
    },
    startTime: a,
    experimental: { standalone: !0 },
  });
}
function ff() {
  return K.addEventListener && K.performance;
}
function J(e) {
  return e / 1e3;
}
function pf(e) {
  let t = `unknown`,
    n = `unknown`,
    r = ``;
  for (let i of e) {
    if (i === `/`) {
      [t, n] = e.split(`/`);
      break;
    }
    if (!isNaN(Number(i))) {
      ((t = r === `h` ? `http` : r), (n = e.split(r)[1]));
      break;
    }
    r += i;
  }
  return (r === e && (t = r), { name: t, version: n });
}
function mf(e) {
  try {
    return PerformanceObserver.supportedEntryTypes.includes(e);
  } catch {
    return !1;
  }
}
function hf(e, t) {
  let n,
    r = !1;
  function i(e) {
    (!r && n && t(e, n), (r = !0));
  }
  Cd(() => {
    i(`pagehide`);
  });
  let a = e.on(`beforeStartNavigationSpan`, (e, t) => {
      t?.isRedirect || (i(`navigation`), a(), o());
    }),
    o = e.on(`afterStartPageLoadSpan`, (e) => {
      ((n = e.spanContext().spanId), o());
    });
}
var gf = e(() => {
  (U(), q(), wd());
});
function _f(e) {
  let t = 0,
    n;
  if (!mf(`layout-shift`)) return;
  let r = Bd(({ metric: e }) => {
    let r = e.entries[e.entries.length - 1];
    r && ((t = e.value), (n = r));
  }, !0);
  hf(e, (e, i) => {
    (vf(t, n, i, e), r());
  });
}
function vf(e, t, n, r) {
  wu && S.log(`Sending CLS span (${e})`);
  let i = t ? J((j() || 0) + t.startTime) : A(),
    a = M().getScopeData().transactionName,
    o = t ? E(t.sources[0]?.node) : `Layout shift`,
    s = {
      [L]: `auto.http.browser.cls`,
      [tr]: `ui.webvital.cls`,
      [sr]: 0,
      "sentry.pageload.span_id": n,
      "sentry.report_event": r,
    };
  t?.sources &&
    t.sources.forEach((e, t) => {
      s[`cls.source.${t + 1}`] = E(e.node);
    });
  let c = df({ name: o, transaction: a, attributes: s, startTime: i });
  c && (c.addEvent(`cls`, { [rr]: ``, [ir]: e }), c.end(i));
}
var yf = e(() => {
  (U(), Tu(), cf(), gf());
});
function bf(e) {
  let t = 0,
    n;
  if (!mf(`largest-contentful-paint`)) return;
  let r = Vd(({ metric: e }) => {
    let r = e.entries[e.entries.length - 1];
    r && ((t = e.value), (n = r));
  }, !0);
  hf(e, (e, i) => {
    (xf(t, n, i, e), r());
  });
}
function xf(e, t, n, r) {
  wu && S.log(`Sending LCP span (${e})`);
  let i = J((j() || 0) + (t?.startTime || 0)),
    a = M().getScopeData().transactionName,
    o = t ? E(t.element) : `Largest contentful paint`,
    s = {
      [L]: `auto.http.browser.lcp`,
      [tr]: `ui.webvital.lcp`,
      [sr]: 0,
      "sentry.pageload.span_id": n,
      "sentry.report_event": r,
    };
  t &&
    (t.element && (s[`lcp.element`] = E(t.element)),
    t.id && (s[`lcp.id`] = t.id),
    t.url && (s[`lcp.url`] = t.url),
    t.loadTime != null && (s[`lcp.loadTime`] = t.loadTime),
    t.renderTime != null && (s[`lcp.renderTime`] = t.renderTime),
    t.size != null && (s[`lcp.size`] = t.size));
  let c = df({ name: o, transaction: a, attributes: s, startTime: i });
  c && (c.addEvent(`lcp`, { [rr]: `millisecond`, [ir]: e }), c.end(i));
}
var Sf = e(() => {
  (U(), Tu(), cf(), gf());
});
function Y(e) {
  return e && ((j() || performance.timeOrigin) + e) / 1e3;
}
function Cf(e) {
  let t = {};
  if (e.nextHopProtocol != null) {
    let { name: n, version: r } = pf(e.nextHopProtocol);
    ((t[`network.protocol.version`] = r), (t[`network.protocol.name`] = n));
  }
  return j() || ff()?.timeOrigin
    ? wf({
        ...t,
        "http.request.redirect_start": Y(e.redirectStart),
        "http.request.redirect_end": Y(e.redirectEnd),
        "http.request.worker_start": Y(e.workerStart),
        "http.request.fetch_start": Y(e.fetchStart),
        "http.request.domain_lookup_start": Y(e.domainLookupStart),
        "http.request.domain_lookup_end": Y(e.domainLookupEnd),
        "http.request.connect_start": Y(e.connectStart),
        "http.request.secure_connection_start": Y(e.secureConnectionStart),
        "http.request.connection_end": Y(e.connectEnd),
        "http.request.request_start": Y(e.requestStart),
        "http.request.response_start": Y(e.responseStart),
        "http.request.response_end": Y(e.responseEnd),
        "http.request.time_to_first_byte":
          e.responseStart == null ? void 0 : e.responseStart / 1e3,
      })
    : t;
}
function wf(e) {
  return Object.fromEntries(Object.entries(e).filter(([, e]) => e != null));
}
var Tf = e(() => {
  (U(), gf());
});
function Ef({
  recordClsStandaloneSpans: e,
  recordLcpStandaloneSpans: t,
  client: n,
}) {
  let r = ff();
  if (r && j()) {
    r.mark && K.performance.mark(`sentry-tracing-init`);
    let i = t ? bf(n) : jf(),
      a = Mf(),
      o = e ? _f(n) : Af();
    return () => {
      (i?.(), a(), o?.());
    };
  }
  return () => void 0;
}
function Df() {
  Wd(`longtask`, ({ entries: e }) => {
    let t = B();
    if (!t) return;
    let { op: n, start_timestamp: r } = R(t);
    for (let i of e) {
      let e = J(j() + i.startTime),
        a = J(i.duration);
      (n === `navigation` && r && e < r) ||
        uf(t, e, e + a, {
          name: `Main UI thread blocked`,
          op: `ui.long-task`,
          attributes: { [L]: `auto.ui.browser.metrics` },
        });
    }
  });
}
function Of() {
  new PerformanceObserver((e) => {
    let t = B();
    if (t)
      for (let n of e.getEntries()) {
        if (!n.scripts[0]) continue;
        let e = J(j() + n.startTime),
          { start_timestamp: r, op: i } = R(t);
        if (i === `navigation` && r && e < r) continue;
        let a = J(n.duration),
          o = { [L]: `auto.ui.browser.metrics` },
          {
            invoker: s,
            invokerType: c,
            sourceURL: l,
            sourceFunctionName: u,
            sourceCharPosition: d,
          } = n.scripts[0];
        ((o[`browser.script.invoker`] = s),
          (o[`browser.script.invoker_type`] = c),
          l && (o[`code.filepath`] = l),
          u && (o[`code.function`] = u),
          d !== -1 && (o[`browser.script.source_char_position`] = d),
          uf(t, e, e + a, {
            name: `Main UI thread blocked`,
            op: `ui.long-animation-frame`,
            attributes: o,
          }));
      }
  }).observe({ type: `long-animation-frame`, buffered: !0 });
}
function kf() {
  Wd(`event`, ({ entries: e }) => {
    let t = B();
    if (t) {
      for (let n of e)
        if (n.name === `click`) {
          let e = J(j() + n.startTime),
            r = J(n.duration),
            i = {
              name: E(n.target),
              op: `ui.interaction.${n.name}`,
              startTime: e,
              attributes: { [L]: `auto.ui.browser.metrics` },
            },
            a = Vt(n.target);
          (a && (i.attributes[`ui.component_name`] = a), uf(t, e, e + r, i));
        }
    }
  });
}
function Af() {
  return Bd(({ metric: e }) => {
    let t = e.entries[e.entries.length - 1];
    t && ((X.cls = { value: e.value, unit: `` }), (Jf = t));
  }, !0);
}
function jf() {
  return Vd(({ metric: e }) => {
    let t = e.entries[e.entries.length - 1];
    t && ((X.lcp = { value: e.value, unit: `millisecond` }), (Z = t));
  }, !0);
}
function Mf() {
  return Hd(({ metric: e }) => {
    e.entries[e.entries.length - 1] &&
      (X.ttfb = { value: e.value, unit: `millisecond` });
  });
}
function Nf(e, t) {
  let n = ff(),
    r = j();
  if (!n?.getEntries || !r) return;
  let i = J(r),
    a = n.getEntries(),
    { op: o, start_timestamp: s } = R(e);
  (a.slice(qf).forEach((n) => {
    let r = J(n.startTime),
      a = J(Math.max(0, n.duration));
    if (!(o === `navigation` && s && i + r < s))
      switch (n.entryType) {
        case `navigation`:
          Lf(e, n, i);
          break;
        case `mark`:
        case `paint`:
        case `measure`: {
          Ff(e, n, r, a, i, t.ignorePerformanceApiSpans);
          let o = Vu(),
            s = n.startTime < o.firstHiddenTime;
          (n.name === `first-paint` &&
            s &&
            (X.fp = { value: n.startTime, unit: `millisecond` }),
            n.name === `first-contentful-paint` &&
              s &&
              (X.fcp = { value: n.startTime, unit: `millisecond` }));
          break;
        }
        case `resource`:
          Vf(e, n, n.name, r, a, i, t.ignoreResourceSpans);
          break;
      }
  }),
    (qf = Math.max(a.length - 1, 0)),
    Hf(e),
    o === `pageload` &&
      (Gf(X),
      t.recordClsOnPageloadSpan || delete X.cls,
      t.recordLcpOnPageloadSpan || delete X.lcp,
      Object.entries(X).forEach(([e, t]) => {
        sa(e, t.value, t.unit);
      }),
      e.setAttribute(`performance.timeOrigin`, i),
      e.setAttribute(`performance.activationStart`, ju()),
      Uf(e, t)),
    (Z = void 0),
    (Jf = void 0),
    (X = {}));
}
function Pf(e) {
  if (e?.entryType === `measure`)
    try {
      return e.detail.devtools.track === `Components ⚛`;
    } catch {
      return;
    }
}
function Ff(e, t, n, r, i, a) {
  if (Pf(t) || ([`mark`, `measure`].includes(t.entryType) && tn(t.name, a)))
    return;
  let o = ku(!1),
    s = J(o ? o.requestStart : 0),
    c = i + Math.max(n, s),
    l = i + n,
    u = l + r,
    d = { [L]: `auto.resource.browser.metrics` };
  (c !== l &&
    ((d[`sentry.browser.measure_happened_before_request`] = !0),
    (d[`sentry.browser.measure_start_time`] = c)),
    If(d, t),
    c <= u && uf(e, c, u, { name: t.name, op: t.entryType, attributes: d }));
}
function If(e, t) {
  try {
    let n = t.detail;
    if (!n) return;
    if (typeof n == `object`) {
      for (let [t, r] of Object.entries(n))
        if (r && Ot(r)) e[`sentry.browser.measure.detail.${t}`] = r;
        else if (r !== void 0)
          try {
            e[`sentry.browser.measure.detail.${t}`] = JSON.stringify(r);
          } catch {}
      return;
    }
    if (Ot(n)) {
      e[`sentry.browser.measure.detail`] = n;
      return;
    }
    try {
      e[`sentry.browser.measure.detail`] = JSON.stringify(n);
    } catch {}
  } catch {}
}
function Lf(e, t, n) {
  ([
    `unloadEvent`,
    `redirect`,
    `domContentLoadedEvent`,
    `loadEvent`,
    `connect`,
  ].forEach((r) => {
    Rf(e, t, r, n);
  }),
    Rf(e, t, `secureConnection`, n, `TLS/SSL`),
    Rf(e, t, `fetch`, n, `cache`),
    Rf(e, t, `domainLookup`, n, `DNS`),
    Bf(e, t, n));
}
function Rf(e, t, n, r, i = n) {
  let a = t[zf(n)],
    o = t[`${n}Start`];
  !o ||
    !a ||
    uf(e, r + J(o), r + J(a), {
      op: `browser.${i}`,
      name: t.name,
      attributes: {
        [L]: `auto.ui.browser.metrics`,
        ...(n === `redirect` && t.redirectCount != null
          ? { "http.redirect_count": t.redirectCount }
          : {}),
      },
    });
}
function zf(e) {
  return e === `secureConnection`
    ? `connectEnd`
    : e === `fetch`
      ? `domainLookupStart`
      : `${e}End`;
}
function Bf(e, t, n) {
  let r = n + J(t.requestStart),
    i = n + J(t.responseEnd),
    a = n + J(t.responseStart);
  t.responseEnd &&
    (uf(e, r, i, {
      op: `browser.request`,
      name: t.name,
      attributes: { [L]: `auto.ui.browser.metrics` },
    }),
    uf(e, a, i, {
      op: `browser.response`,
      name: t.name,
      attributes: { [L]: `auto.ui.browser.metrics` },
    }));
}
function Vf(e, t, n, r, i, a, o) {
  if (t.initiatorType === `xmlhttprequest` || t.initiatorType === `fetch`)
    return;
  let s = t.initiatorType ? `resource.${t.initiatorType}` : `resource.other`;
  if (o?.includes(s)) return;
  let c = { [L]: `auto.resource.browser.metrics` },
    l = tc(n);
  (l.protocol && (c[`url.scheme`] = l.protocol.split(`:`).pop()),
    l.host && (c[`server.address`] = l.host),
    (c[`url.same_origin`] = n.includes(K.location.origin)),
    Wf(t, c, [
      [`responseStatus`, `http.response.status_code`],
      [`transferSize`, `http.response_transfer_size`],
      [`encodedBodySize`, `http.response_content_length`],
      [`decodedBodySize`, `http.decoded_response_content_length`],
      [`renderBlockingStatus`, `resource.render_blocking_status`],
      [`deliveryType`, `http.response_delivery_type`],
    ]));
  let u = { ...c, ...Cf(t) },
    d = a + r;
  uf(e, d, d + i, {
    name: n.replace(K.location.origin, ``),
    op: s,
    attributes: u,
  });
}
function Hf(e) {
  let t = K.navigator;
  if (!t) return;
  let n = t.connection;
  (n &&
    (n.effectiveType &&
      e.setAttribute(`effectiveConnectionType`, n.effectiveType),
    n.type && e.setAttribute(`connectionType`, n.type),
    lf(n.rtt) && (X[`connection.rtt`] = { value: n.rtt, unit: `millisecond` })),
    lf(t.deviceMemory) &&
      e.setAttribute(`deviceMemory`, `${t.deviceMemory} GB`),
    lf(t.hardwareConcurrency) &&
      e.setAttribute(`hardwareConcurrency`, String(t.hardwareConcurrency)));
}
function Uf(e, t) {
  (Z &&
    t.recordLcpOnPageloadSpan &&
    (Z.element && e.setAttribute(`lcp.element`, E(Z.element)),
    Z.id && e.setAttribute(`lcp.id`, Z.id),
    Z.url && e.setAttribute(`lcp.url`, Z.url.trim().slice(0, 200)),
    Z.loadTime != null && e.setAttribute(`lcp.loadTime`, Z.loadTime),
    Z.renderTime != null && e.setAttribute(`lcp.renderTime`, Z.renderTime),
    e.setAttribute(`lcp.size`, Z.size)),
    Jf?.sources &&
      t.recordClsOnPageloadSpan &&
      Jf.sources.forEach((t, n) =>
        e.setAttribute(`cls.source.${n + 1}`, E(t.node)),
      ));
}
function Wf(e, t, n) {
  n.forEach(([n, r]) => {
    let i = e[n];
    i != null &&
      ((typeof i == `number` && i < Kf) || typeof i == `string`) &&
      (t[r] = i);
  });
}
function Gf(e) {
  let t = ku(!1);
  if (!t) return;
  let { responseStart: n, requestStart: r } = t;
  r <= n && (e[`ttfb.requestTime`] = { value: n - r, unit: `millisecond` });
}
var Kf,
  qf,
  X,
  Z,
  Jf,
  Yf = e(() => {
    (U(),
      q(),
      yf(),
      cf(),
      Sf(),
      Tf(),
      gf(),
      Mu(),
      Au(),
      Hu(),
      (Kf = 2147483647),
      (qf = 0),
      (X = {}));
  });
function Xf() {
  return ff() && j() ? Wd(`element`, Zf) : () => void 0;
}
var Zf,
  Qf = e(() => {
    (U(),
      cf(),
      gf(),
      (Zf = ({ entries: e }) => {
        let t = B(),
          n = t ? z(t) : void 0,
          r = n ? R(n).description : M().getScopeData().transactionName;
        e.forEach((e) => {
          let t = e;
          if (!t.identifier) return;
          let n = t.name,
            i = t.renderTime,
            a = t.loadTime,
            [o, s] = a
              ? [J(a), `load-time`]
              : i
                ? [J(i), `render-time`]
                : [A(), `entry-emission`],
            c = n === `image-paint` ? J(Math.max(0, (i ?? 0) - (a ?? 0))) : 0,
            l = {
              [L]: `auto.ui.browser.elementtiming`,
              [tr]: `ui.elementtiming`,
              [I]: `component`,
              "sentry.span_start_time_source": s,
              "sentry.transaction_name": r,
              "element.id": t.id,
              "element.type": t.element?.tagName?.toLowerCase() || `unknown`,
              "element.size":
                t.naturalWidth && t.naturalHeight
                  ? `${t.naturalWidth}x${t.naturalHeight}`
                  : void 0,
              "element.render_time": i,
              "element.load_time": a,
              "element.url": t.url || void 0,
              "element.identifier": t.identifier,
              "element.paint_type": n,
            };
          Sa(
            {
              name: `element[${t.identifier}]`,
              attributes: l,
              startTime: o,
              onlyIfParent: !0,
            },
            (e) => {
              e.end(o + c);
            },
          );
        });
      }));
  });
function $f(e) {
  (ct(`dom`, e), lt(`dom`, ep));
}
function ep() {
  if (!K.document) return;
  let e = w.bind(null, `dom`),
    t = rp(e, !0);
  (K.document.addEventListener(`click`, t, !1),
    K.document.addEventListener(`keypress`, t, !1),
    [`EventTarget`, `Node`].forEach((t) => {
      let n = K[t]?.prototype;
      n?.hasOwnProperty?.(`addEventListener`) &&
        (D(n, `addEventListener`, function (t) {
          return function (n, r, i) {
            if (n === `click` || n == `keypress`)
              try {
                let r = (this.__sentry_instrumentation_handlers__ =
                    this.__sentry_instrumentation_handlers__ || {}),
                  a = (r[n] = r[n] || { refCount: 0 });
                if (!a.handler) {
                  let r = rp(e);
                  ((a.handler = r), t.call(this, n, r, i));
                }
                a.refCount++;
              } catch {}
            return t.call(this, n, r, i);
          };
        }),
        D(n, `removeEventListener`, function (e) {
          return function (t, n, r) {
            if (t === `click` || t == `keypress`)
              try {
                let n = this.__sentry_instrumentation_handlers__ || {},
                  i = n[t];
                i &&
                  (i.refCount--,
                  i.refCount <= 0 &&
                    (e.call(this, t, i.handler, r),
                    (i.handler = void 0),
                    delete n[t]),
                  Object.keys(n).length === 0 &&
                    delete this.__sentry_instrumentation_handlers__);
              } catch {}
            return e.call(this, t, n, r);
          };
        }));
    }));
}
function tp(e) {
  if (e.type !== sp) return !1;
  try {
    if (!e.target || e.target._sentryId !== cp) return !1;
  } catch {}
  return !0;
}
function np(e, t) {
  return e === `keypress`
    ? t?.tagName
      ? !(
          t.tagName === `INPUT` ||
          t.tagName === `TEXTAREA` ||
          t.isContentEditable
        )
      : !0
    : !1;
}
function rp(e, t = !1) {
  return (n) => {
    if (!n || n._sentryCaptured) return;
    let r = ip(n);
    if (np(n.type, r)) return;
    (O(n, `_sentryCaptured`, !0), r && !r._sentryId && O(r, `_sentryId`, k()));
    let i = n.type === `keypress` ? `input` : n.type;
    (tp(n) ||
      (e({ event: n, name: i, global: t }),
      (sp = n.type),
      (cp = r ? r._sentryId : void 0)),
      clearTimeout(op),
      (op = K.setTimeout(() => {
        ((cp = void 0), (sp = void 0));
      }, ap)));
  };
}
function ip(e) {
  try {
    return e.target;
  } catch {
    return null;
  }
}
var ap,
  op,
  sp,
  cp,
  lp = e(() => {
    (U(), q(), (ap = 1e3));
  });
function up(e) {
  let t = `history`;
  (ct(t, e), lt(t, dp));
}
function dp() {
  if (
    (K.addEventListener(`popstate`, () => {
      let e = K.location.href,
        t = pp;
      ((pp = e), t !== e && w(`history`, { from: t, to: e }));
    }),
    !pl())
  )
    return;
  function e(e) {
    return function (...t) {
      let n = t.length > 2 ? t[2] : void 0;
      if (n) {
        let r = pp,
          i = fp(String(n));
        if (((pp = i), r === i)) return e.apply(this, t);
        w(`history`, { from: r, to: i });
      }
      return e.apply(this, t);
    };
  }
  (D(K.history, `pushState`, e), D(K.history, `replaceState`, e));
}
function fp(e) {
  try {
    return new URL(e, K.location.origin).toString();
  } catch {
    return e;
  }
}
var pp,
  mp = e(() => {
    (U(), q());
  });
function hp(e) {
  let t = _p[e];
  if (t) return t;
  let n = K[e];
  if (hl(n)) return (_p[e] = n.bind(K));
  let r = K.document;
  if (r && typeof r.createElement == `function`)
    try {
      let t = r.createElement(`iframe`);
      ((t.hidden = !0), r.head.appendChild(t));
      let i = t.contentWindow;
      (i?.[e] && (n = i[e]), r.head.removeChild(t));
    } catch (t) {
      wu &&
        S.warn(
          `Could not create sandbox iframe for ${e} check, bailing to window.${e}: `,
          t,
        );
    }
  return n && (_p[e] = n.bind(K));
}
function gp(e) {
  _p[e] = void 0;
}
var _p,
  vp = e(() => {
    (U(), Tu(), q(), (_p = {}));
  });
function yp(e) {
  (ct(`xhr`, e), lt(`xhr`, bp));
}
function bp() {
  if (!K.XMLHttpRequest) return;
  let e = XMLHttpRequest.prototype;
  ((e.open = new Proxy(e.open, {
    apply(e, t, n) {
      let r = Error(),
        i = A() * 1e3,
        a = Et(n[0]) ? n[0].toUpperCase() : void 0,
        o = xp(n[1]);
      if (!a || !o) return e.apply(t, n);
      ((t[Sp] = { method: a, url: o, request_headers: {} }),
        a === `POST` &&
          o.match(/sentry_key/) &&
          (t.__sentry_own_request__ = !0));
      let s = () => {
        let e = t[Sp];
        if (e && t.readyState === 4) {
          try {
            e.status_code = t.status;
          } catch {}
          w(`xhr`, {
            endTimestamp: A() * 1e3,
            startTimestamp: i,
            xhr: t,
            virtualError: r,
          });
        }
      };
      return (
        `onreadystatechange` in t && typeof t.onreadystatechange == `function`
          ? (t.onreadystatechange = new Proxy(t.onreadystatechange, {
              apply(e, t, n) {
                return (s(), e.apply(t, n));
              },
            }))
          : t.addEventListener(`readystatechange`, s),
        (t.setRequestHeader = new Proxy(t.setRequestHeader, {
          apply(e, t, n) {
            let [r, i] = n,
              a = t[Sp];
            return (
              a && Et(r) && Et(i) && (a.request_headers[r.toLowerCase()] = i),
              e.apply(t, n)
            );
          },
        })),
        e.apply(t, n)
      );
    },
  })),
    (e.send = new Proxy(e.send, {
      apply(e, t, n) {
        let r = t[Sp];
        return r
          ? (n[0] !== void 0 && (r.body = n[0]),
            w(`xhr`, { startTimestamp: A() * 1e3, xhr: t }),
            e.apply(t, n))
          : e.apply(t, n);
      },
    })));
}
function xp(e) {
  if (Et(e)) return e;
  try {
    return e.toString();
  } catch {}
}
var Sp,
  Cp = e(() => {
    (U(), q(), (Sp = `__sentry_xhr_v3__`));
  });
function wp(e) {
  let t;
  try {
    t = e.getAllResponseHeaders();
  } catch (t) {
    return (wu && S.error(t, `Failed to get xhr response headers`, e), {});
  }
  return t
    ? t
        .split(
          `\r
`,
        )
        .reduce((e, t) => {
          let [n, r] = t.split(`: `);
          return (r && (e[n.toLowerCase()] = r), e);
        }, {})
    : {};
}
var Tp = e(() => {
  (U(), Tu());
});
function Ep() {
  if (ff() && j()) {
    let e = Dp();
    return () => {
      e();
    };
  }
  return () => void 0;
}
function Dp() {
  return Ud(Pp);
}
function Op() {
  let e = Object.keys(Np);
  Pl() &&
    e.forEach((e) => {
      K.addEventListener(e, t, { capture: !0, passive: !0 });
    });
  function t(e) {
    let t = e.target;
    if (!t) return;
    let n = E(t),
      r = Math.round(e.timeStamp);
    if ((jp.set(r, n), jp.size > 50)) {
      let e = jp.keys().next().value;
      e !== void 0 && jp.delete(e);
    }
  }
  function n(e) {
    let t = Math.round(e.startTime),
      n = jp.get(t);
    if (!n)
      for (let e = -5; e <= 5; e++) {
        let r = jp.get(t + e);
        if (r) {
          n = r;
          break;
        }
      }
    return n || `<unknown>`;
  }
  let r = ({ entries: e }) => {
    let t = B(),
      r = t && z(t);
    e.forEach((e) => {
      if (!ef(e)) return;
      let t = e.interactionId;
      if (t == null || Ap.has(t)) return;
      let i = e.target ? E(e.target) : n(e);
      if (kp.length > 10) {
        let e = kp.shift();
        Ap.delete(e);
      }
      (kp.push(t), Ap.set(t, { span: r, elementName: i }));
    });
  };
  (Wd(`event`, r), Wd(`first-input`, r));
}
var kp,
  Ap,
  jp,
  Mp,
  Np,
  Pp,
  Fp = e(() => {
    (U(),
      q(),
      cf(),
      gf(),
      (kp = []),
      (Ap = new Map()),
      (jp = new Map()),
      (Mp = 60),
      (Np = {
        click: `click`,
        pointerdown: `click`,
        pointerup: `click`,
        mousedown: `click`,
        mouseup: `click`,
        touchstart: `click`,
        touchend: `click`,
        mouseover: `hover`,
        mouseout: `hover`,
        mouseenter: `hover`,
        mouseleave: `hover`,
        pointerover: `hover`,
        pointerout: `hover`,
        pointerenter: `hover`,
        pointerleave: `hover`,
        dragstart: `drag`,
        dragend: `drag`,
        drag: `drag`,
        dragenter: `drag`,
        dragleave: `drag`,
        dragover: `drag`,
        drop: `drag`,
        keydown: `press`,
        keyup: `press`,
        keypress: `press`,
        input: `press`,
      }),
      (Pp = ({ metric: e }) => {
        if (e.value == null) return;
        let t = J(e.value);
        if (t > Mp) return;
        let n = e.entries.find((t) => t.duration === e.value && Np[t.name]);
        if (!n) return;
        let { interactionId: r } = n,
          i = Np[n.name],
          a = J(j() + n.startTime),
          o = B(),
          s = o ? z(o) : void 0,
          c = r == null ? void 0 : Ap.get(r),
          l = c?.span || s,
          u = l ? R(l).description : M().getScopeData().transactionName,
          d = df({
            name: c?.elementName || E(n.target),
            transaction: u,
            attributes: {
              [L]: `auto.http.browser.inp`,
              [tr]: `ui.interaction.${i}`,
              [sr]: n.duration,
            },
            startTime: a,
          });
        d &&
          (d.addEvent(`inp`, { [rr]: `millisecond`, [ir]: e.value }),
          d.end(a + t));
      }));
  }),
  Ip = e(() => {
    (cf(), Yf(), Qf(), lp(), mp(), vp(), Cp(), Tp(), Tf(), Fp());
  });
function Lp(e, t = hp(`fetch`)) {
  let n = 0,
    r = 0;
  async function i(i) {
    let a = i.body.length;
    ((n += a), r++);
    let o = {
      body: i.body,
      method: `POST`,
      referrerPolicy: `strict-origin`,
      headers: e.headers,
      keepalive: n <= 6e4 && r < 15,
      ...e.fetchOptions,
    };
    try {
      let n = await t(e.url, o);
      return {
        statusCode: n.status,
        headers: {
          "x-sentry-rate-limits": n.headers.get(`X-Sentry-Rate-Limits`),
          "retry-after": n.headers.get(`Retry-After`),
        },
      };
    } catch (e) {
      throw (gp(`fetch`), e);
    } finally {
      ((n -= a), r--);
    }
  }
  return ys(e, i, us(e.bufferSize || Rp));
}
var Rp,
  zp = e(() => {
    (U(), Ip(), (Rp = 40));
  }),
  Q,
  Bp = e(() => {
    Q = typeof __SENTRY_DEBUG__ > `u` || __SENTRY_DEBUG__;
  });
function Vp(e, t, n, r) {
  let i = { filename: e, function: t === `<anonymous>` ? `?` : t, in_app: !0 };
  return (n !== void 0 && (i.lineno = n), r !== void 0 && (i.colno = r), i);
}
var Hp,
  Up,
  Wp,
  Gp,
  Kp,
  qp,
  Jp,
  Yp,
  Xp,
  Zp,
  Qp,
  $p,
  em,
  tm,
  nm,
  rm = e(() => {
    (U(),
      (Hp = 30),
      (Up = 50),
      (Wp = /^\s*at (\S+?)(?::(\d+))(?::(\d+))\s*$/i),
      (Gp =
        /^\s*at (?:(.+?\)(?: \[.+\])?|.*?) ?\((?:address at )?)?(?:async )?((?:<anonymous>|[-a-z]+:|.*bundle|\/)?.*?)(?::(\d+))?(?::(\d+))?\)?\s*$/i),
      (Kp = /\((\S*)(?::(\d+))(?::(\d+))\)/),
      (qp = /at (.+?) ?\(data:(.+?),/),
      (Jp = (e) => {
        let t = e.match(qp);
        if (t) return { filename: `<data:${t[2]}>`, function: t[1] };
        let n = Wp.exec(e);
        if (n) {
          let [, e, t, r] = n;
          return Vp(e, `?`, +t, +r);
        }
        let r = Gp.exec(e);
        if (r) {
          if (r[2] && r[2].indexOf(`eval`) === 0) {
            let e = Kp.exec(r[2]);
            e && ((r[2] = e[1]), (r[3] = e[2]), (r[4] = e[3]));
          }
          let [e, t] = nm(r[1] || `?`, r[2]);
          return Vp(t, e, r[3] ? +r[3] : void 0, r[4] ? +r[4] : void 0);
        }
      }),
      (Yp = [Hp, Jp]),
      (Xp =
        /^\s*(.*?)(?:\((.*?)\))?(?:^|@)?((?:[-a-z]+)?:\/.*?|\[native code\]|[^@]*(?:bundle|\d+\.js)|\/[\w\-. /=]+)(?::(\d+))?(?::(\d+))?\s*$/i),
      (Zp = /(\S+) line (\d+)(?: > eval line \d+)* > eval/i),
      (Qp = (e) => {
        let t = Xp.exec(e);
        if (t) {
          if (t[3] && t[3].indexOf(` > eval`) > -1) {
            let e = Zp.exec(t[3]);
            e &&
              ((t[1] = t[1] || `eval`),
              (t[3] = e[1]),
              (t[4] = e[2]),
              (t[5] = ``));
          }
          let e = t[3],
            n = t[1] || `?`;
          return (
            ([n, e] = nm(n, e)),
            Vp(e, n, t[4] ? +t[4] : void 0, t[5] ? +t[5] : void 0)
          );
        }
      }),
      ($p = [Up, Qp]),
      (em = [Yp, $p]),
      (tm = Xe(...em)),
      (nm = (e, t) => {
        let n = e.indexOf(`safari-extension`) !== -1,
          r = e.indexOf(`safari-web-extension`) !== -1;
        return n || r
          ? [
              e.indexOf(`@`) === -1 ? `?` : e.split(`@`)[0],
              n ? `safari-extension:${t}` : `safari-web-extension:${t}`,
            ]
          : [e, t];
      }));
  });
function im(e) {
  return function (t) {
    P() === e &&
      pc(
        {
          category: `sentry.${t.type === `transaction` ? `transaction` : `event`}`,
          event_id: t.event_id,
          level: t.level,
          message: sn(t),
        },
        { event: t },
      );
  };
}
function am(e, t) {
  return function (n) {
    if (P() !== e) return;
    let r,
      i,
      a = typeof t == `object` ? t.serializeAttribute : void 0,
      o =
        typeof t == `object` && typeof t.maxStringLength == `number`
          ? t.maxStringLength
          : void 0;
    (o &&
      o > dm &&
      (Q &&
        S.warn(
          `\`dom.maxStringLength\` cannot exceed ${dm}, but a value of ${o} was configured. Sentry will use ${dm} instead.`,
        ),
      (o = dm)),
      typeof a == `string` && (a = [a]));
    try {
      let e = n.event,
        t = um(e) ? e.target : e;
      ((r = E(t, { keyAttrs: a, maxStringLength: o })), (i = Vt(t)));
    } catch {
      r = `<unknown>`;
    }
    if (r.length === 0) return;
    let s = { category: `ui.${n.name}`, message: r };
    (i && (s.data = { "ui.component_name": i }),
      pc(s, { event: n.event, name: n.name, global: n.global }));
  };
}
function om(e) {
  return function (t) {
    if (P() !== e) return;
    let n = {
      category: `console`,
      data: { arguments: t.args, logger: `console` },
      level: Wc(t.level),
      message: $t(t.args, ` `),
    };
    if (t.level === `assert`)
      if (t.args[0] === !1)
        ((n.message = `Assertion failed: ${$t(t.args.slice(1), ` `) || `console.assert`}`),
          (n.data.arguments = t.args.slice(1)));
      else return;
    pc(n, { input: t.args, level: t.level });
  };
}
function sm(e) {
  return function (t) {
    if (P() !== e) return;
    let { startTimestamp: n, endTimestamp: r } = t,
      i = t.xhr[Sp];
    if (!n || !r || !i) return;
    let { method: a, url: o, status_code: s, body: c } = i,
      l = { method: a, url: o, status_code: s },
      u = { xhr: t.xhr, input: c, startTimestamp: n, endTimestamp: r },
      d = { category: `xhr`, data: l, type: `http`, level: dl(s) };
    (e.emit(`beforeOutgoingRequestBreadcrumb`, d, u), pc(d, u));
  };
}
function cm(e) {
  return function (t) {
    if (P() !== e) return;
    let { startTimestamp: n, endTimestamp: r } = t;
    if (
      r &&
      !(t.fetchData.url.match(/sentry_key/) && t.fetchData.method === `POST`)
    )
      if ((t.fetchData.method, t.fetchData.url, t.error)) {
        let i = t.fetchData,
          a = {
            data: t.error,
            input: t.args,
            startTimestamp: n,
            endTimestamp: r,
          },
          o = { category: `fetch`, data: i, level: `error`, type: `http` };
        (e.emit(`beforeOutgoingRequestBreadcrumb`, o, a), pc(o, a));
      } else {
        let i = t.response,
          a = { ...t.fetchData, status_code: i?.status };
        (t.fetchData.request_body_size,
          t.fetchData.response_body_size,
          i?.status);
        let o = {
            input: t.args,
            response: i,
            startTimestamp: n,
            endTimestamp: r,
          },
          s = {
            category: `fetch`,
            data: a,
            type: `http`,
            level: dl(a.status_code),
          };
        (e.emit(`beforeOutgoingRequestBreadcrumb`, s, o), pc(s, o));
      }
  };
}
function lm(e) {
  return function (t) {
    if (P() !== e) return;
    let n = t.from,
      r = t.to,
      i = tc(W.location.href),
      a = n ? tc(n) : void 0,
      o = tc(r);
    (a?.path || (a = i),
      i.protocol === o.protocol && i.host === o.host && (r = o.relative),
      i.protocol === a.protocol && i.host === a.host && (n = a.relative),
      pc({ category: `navigation`, data: { from: n, to: r } }));
  };
}
function um(e) {
  return !!e && !!e.target;
}
var dm,
  fm,
  pm,
  mm,
  hm = e(() => {
    (U(),
      Ip(),
      Bp(),
      G(),
      (dm = 1024),
      (fm = `Breadcrumbs`),
      (pm = (e = {}) => {
        let t = {
          console: !0,
          dom: !0,
          fetch: !0,
          history: !0,
          sentry: !0,
          xhr: !0,
          ...e,
        };
        return {
          name: fm,
          setup(e) {
            (t.console && Vc(om(e)),
              t.dom && $f(am(e, t.dom)),
              t.xhr && yp(sm(e)),
              t.fetch && yl(cm(e)),
              t.history && up(lm(e)),
              t.sentry && e.on(`beforeSendEvent`, im(e)));
          },
        };
      }),
      (mm = qo(pm)));
  });
function gm(e) {
  return function (...t) {
    let n = t[0];
    return (
      (t[0] = eu(n, {
        mechanism: {
          handled: !1,
          type: `auto.browser.browserapierrors.${et(e)}`,
        },
      })),
      e.apply(this, t)
    );
  };
}
function _m(e) {
  return function (t) {
    return e.apply(this, [
      eu(t, {
        mechanism: {
          data: { handler: et(e) },
          handled: !1,
          type: `auto.browser.browserapierrors.requestAnimationFrame`,
        },
      }),
    ]);
  };
}
function vm(e) {
  return function (...t) {
    let n = this;
    return (
      [`onload`, `onerror`, `onprogress`, `onreadystatechange`].forEach((e) => {
        e in n &&
          typeof n[e] == `function` &&
          D(n, e, function (t) {
            let n = {
                mechanism: {
                  data: { handler: et(t) },
                  handled: !1,
                  type: `auto.browser.browserapierrors.xhr.${e}`,
                },
              },
              r = Kt(t);
            return (r && (n.mechanism.data.handler = et(r)), eu(t, n));
          });
      }),
      e.apply(this, t)
    );
  };
}
function ym(e, t) {
  let n = W[e]?.prototype;
  n?.hasOwnProperty?.(`addEventListener`) &&
    (D(n, `addEventListener`, function (n) {
      return function (r, i, a) {
        try {
          bm(i) &&
            (i.handleEvent = eu(i.handleEvent, {
              mechanism: {
                data: { handler: et(i), target: e },
                handled: !1,
                type: `auto.browser.browserapierrors.handleEvent`,
              },
            }));
        } catch {}
        return (
          t.unregisterOriginalCallbacks && xm(this, r, i),
          n.apply(this, [
            r,
            eu(i, {
              mechanism: {
                data: { handler: et(i), target: e },
                handled: !1,
                type: `auto.browser.browserapierrors.addEventListener`,
              },
            }),
            a,
          ])
        );
      };
    }),
    D(n, `removeEventListener`, function (e) {
      return function (t, n, r) {
        try {
          let i = n.__sentry_wrapped__;
          i && e.call(this, t, i, r);
        } catch {}
        return e.call(this, t, n, r);
      };
    }));
}
function bm(e) {
  return typeof e.handleEvent == `function`;
}
function xm(e, t, n) {
  e &&
    typeof e == `object` &&
    `removeEventListener` in e &&
    typeof e.removeEventListener == `function` &&
    e.removeEventListener(t, n);
}
var Sm,
  Cm,
  wm,
  Tm,
  Em = e(() => {
    (U(),
      G(),
      (Sm =
        `EventTarget.Window.Node.ApplicationCache.AudioTrackList.BroadcastChannel.ChannelMergerNode.CryptoOperation.EventSource.FileReader.HTMLUnknownElement.IDBDatabase.IDBRequest.IDBTransaction.KeyOperation.MediaController.MessagePort.ModalWindow.Notification.SVGElementInstance.Screen.SharedWorker.TextTrack.TextTrackCue.TextTrackList.WebSocket.WebSocketWorker.Worker.XMLHttpRequest.XMLHttpRequestEventTarget.XMLHttpRequestUpload`.split(
          `.`,
        )),
      (Cm = `BrowserApiErrors`),
      (wm = (e = {}) => {
        let t = {
          XMLHttpRequest: !0,
          eventTarget: !0,
          requestAnimationFrame: !0,
          setInterval: !0,
          setTimeout: !0,
          unregisterOriginalCallbacks: !1,
          ...e,
        };
        return {
          name: Cm,
          setupOnce() {
            (t.setTimeout && D(W, `setTimeout`, gm),
              t.setInterval && D(W, `setInterval`, gm),
              t.requestAnimationFrame && D(W, `requestAnimationFrame`, _m),
              t.XMLHttpRequest &&
                `XMLHttpRequest` in W &&
                D(XMLHttpRequest.prototype, `send`, vm));
            let e = t.eventTarget;
            e && (Array.isArray(e) ? e : Sm).forEach((e) => ym(e, t));
          },
        };
      }),
      (Tm = qo(wm)));
  }),
  Dm,
  Om = e(() => {
    (U(),
      Ip(),
      Bp(),
      G(),
      (Dm = qo(() => ({
        name: `BrowserSession`,
        setupOnce() {
          if (W.document === void 0) {
            Q &&
              S.warn(
                "Using the `browserSessionIntegration` in non-browser environments is not supported.",
              );
            return;
          }
          (jo({ ignoreDuration: !0 }),
            Po(),
            up(({ from: e, to: t }) => {
              e !== void 0 && e !== t && (jo({ ignoreDuration: !0 }), Po());
            }));
        },
      }))));
  });
function km(e) {
  pt((t) => {
    let { stackParser: n, attachStacktrace: r } = Fm();
    if (P() !== e || Ql()) return;
    let { msg: i, url: a, line: o, column: s, error: c } = t,
      l = Nm(mu(n, c || i, void 0, r, !1), a, o, s);
    ((l.level = `error`),
      Do(l, {
        originalException: c,
        mechanism: {
          handled: !1,
          type: `auto.browser.global_handlers.onerror`,
        },
      }));
  });
}
function Am(e) {
  _t((t) => {
    let { stackParser: n, attachStacktrace: r } = Fm();
    if (P() !== e || Ql()) return;
    let i = jm(t),
      a = Ot(i) ? Mm(i) : mu(n, i, void 0, r, !0);
    ((a.level = `error`),
      Do(a, {
        originalException: i,
        mechanism: {
          handled: !1,
          type: `auto.browser.global_handlers.onunhandledrejection`,
        },
      }));
  });
}
function jm(e) {
  if (Ot(e)) return e;
  try {
    if (`reason` in e) return e.reason;
    if (`detail` in e && `reason` in e.detail) return e.detail.reason;
  } catch {}
  return e;
}
function Mm(e) {
  return {
    exception: {
      values: [
        {
          type: `UnhandledRejection`,
          value: `Non-Error promise rejection captured with value: ${String(e)}`,
        },
      ],
    },
  };
}
function Nm(e, t, n, r) {
  let i = (e.exception = e.exception || {}),
    a = (i.values = i.values || []),
    o = (a[0] = a[0] || {}),
    s = (o.stacktrace = o.stacktrace || {}),
    c = (s.frames = s.frames || []),
    l = r,
    u = n,
    d = Im(t) ?? Bt();
  return (
    c.length === 0 &&
      c.push({ colno: l, filename: d, function: `?`, in_app: !0, lineno: u }),
    e
  );
}
function Pm(e) {
  Q && S.log(`Global Handler attached: ${e}`);
}
function Fm() {
  return P()?.getOptions() || { stackParser: () => [], attachStacktrace: !1 };
}
function Im(e) {
  if (!(!Et(e) || e.length === 0)) {
    if (e.startsWith(`data:`)) {
      let t = e.match(/^data:([^;]+)/);
      return `<data:${t ? t[1] : `text/javascript`}${e.includes(`base64,`) ? `,base64` : ``}>`;
    }
    return e;
  }
}
var Lm,
  Rm,
  zm,
  Bm = e(() => {
    (U(),
      Bp(),
      bu(),
      G(),
      (Lm = `GlobalHandlers`),
      (Rm = (e = {}) => {
        let t = { onerror: !0, onunhandledrejection: !0, ...e };
        return {
          name: Lm,
          setupOnce() {
            Error.stackTraceLimit = 50;
          },
          setup(e) {
            (t.onerror && (km(e), Pm(`onerror`)),
              t.onunhandledrejection && (Am(e), Pm(`onunhandledrejection`)));
          },
        };
      }),
      (zm = qo(Rm)));
  }),
  Vm,
  Hm = e(() => {
    (U(),
      G(),
      (Vm = qo(() => ({
        name: `HttpContext`,
        preprocessEvent(e) {
          if (!W.navigator && !W.location && !W.document) return;
          let t = tu(),
            n = { ...t.headers, ...e.request?.headers };
          e.request = { ...t, ...e.request, headers: n };
        },
      }))));
  }),
  Um,
  Wm,
  Gm,
  Km,
  qm,
  Jm = e(() => {
    (U(),
      bu(),
      (Um = `cause`),
      (Wm = 5),
      (Gm = `LinkedErrors`),
      (Km = (e = {}) => {
        let t = e.limit || Wm,
          n = e.key || Um;
        return {
          name: Gm,
          preprocessEvent(e, r, i) {
            Ic(ru, i.getOptions().stackParser, n, t, e, r);
          },
        };
      }),
      (qm = qo(Km)));
  });
function Ym() {
  return Xm()
    ? (Q &&
        Re(() => {
          console.error(
            `[Sentry] You cannot use Sentry.init() in a browser extension, see: https://docs.sentry.io/platforms/javascript/best-practices/browser-extensions/`,
          );
        }),
      !0)
    : !1;
}
function Xm() {
  if (W.window === void 0) return !1;
  let e = W;
  if (e.nw || !(e.chrome || e.browser)?.runtime?.id) return !1;
  let t = Bt();
  return !(
    W === W.top &&
    [
      `chrome-extension`,
      `moz-extension`,
      `ms-browser-extension`,
      `safari-web-extension`,
    ].some((e) => t.startsWith(`${e}://`))
  );
}
var Zm = e(() => {
  (U(), Bp(), G());
});
function Qm(e) {
  return [Pc(), bc(), Tm(), mm(), zm(), qm(), el(), Vm(), Dm()];
}
function $m(e = {}) {
  let t = !e.skipBrowserExtensionCheck && Ym(),
    n = e.defaultIntegrations == null ? Qm() : e.defaultIntegrations;
  return Ys(Su, {
    ...e,
    enabled: t ? !1 : e.enabled,
    stackParser: Ze(e.stackParser || tm),
    integrations: Uo({ integrations: e.integrations, defaultIntegrations: n }),
    transport: e.transport || Lp,
  });
}
var eh = e(() => {
  (U(), Cu(), hm(), Em(), Om(), Bm(), Hm(), Jm(), rm(), zp(), Zm());
});
function th(e) {
  return e.split(`,`).some((e) => e.trim().startsWith(`sentry-`));
}
function nh(e) {
  try {
    return new URL(e, W.location.origin).href;
  } catch {
    return;
  }
}
function rh(e) {
  return (
    e.entryType === `resource` &&
    `initiatorType` in e &&
    typeof e.nextHopProtocol == `string` &&
    (e.initiatorType === `fetch` || e.initiatorType === `xmlhttprequest`)
  );
}
function ih(e) {
  try {
    return new Headers(e);
  } catch {
    return;
  }
}
var ah = e(() => {
  G();
});
function oh(e, t) {
  let {
      traceFetch: n,
      traceXHR: r,
      trackFetchStreamPerformance: i,
      shouldCreateSpanForRequest: a,
      enableHTTPTimings: o,
      tracePropagationTargets: s,
      onRequestSpanStart: c,
      onRequestSpanEnd: l,
    } = { ...mh, ...t },
    u = typeof a == `function` ? a : (e) => !0,
    d = (e) => ch(e, s),
    f = {},
    p = e.getOptions().propagateTraceparent;
  (n &&
    (e.addEventProcessor(
      (e) => (
        e.type === `transaction` &&
          e.spans &&
          e.spans.forEach((e) => {
            if (e.op === `http.client`) {
              let t = ph.get(e.span_id);
              t && ((e.timestamp = t / 1e3), ph.delete(e.span_id));
            }
          }),
        e
      ),
    ),
    i &&
      bl((e) => {
        if (e.response) {
          let t = fh.get(e.response);
          t && e.endTimestamp && ph.set(t, e.endTimestamp);
        }
      }),
    yl((e) => {
      let t = nl(e, u, d, f, { propagateTraceparent: p, onRequestSpanEnd: l });
      if (
        (e.response &&
          e.fetchData.__span &&
          fh.set(e.response, e.fetchData.__span),
        t)
      ) {
        let n = nh(e.fetchData.url),
          r = n ? tc(n).host : void 0;
        (t.setAttributes({ "http.url": n, "server.address": r }),
          o && sh(t),
          c?.(t, { headers: e.headers }));
      }
    })),
    r &&
      yp((e) => {
        let t = lh(e, u, d, f, p, l);
        t &&
          (o && sh(t),
          c?.(t, { headers: ih(e.xhr.__sentry_xhr_v3__?.request_headers) }));
      }));
}
function sh(e) {
  let { url: t } = R(e).data;
  if (!t || typeof t != `string`) return;
  let n = Wd(`resource`, ({ entries: r }) => {
    r.forEach((r) => {
      rh(r) && r.name.endsWith(t) && (e.setAttributes(Cf(r)), setTimeout(n));
    });
  });
}
function ch(e, t) {
  let n = Bt();
  if (n) {
    let r, i;
    try {
      ((r = new URL(e, n)), (i = new URL(n).origin));
    } catch {
      return !1;
    }
    let a = r.origin === i;
    return t ? tn(r.toString(), t) || (a && tn(r.pathname, t)) : a;
  } else {
    let n = !!e.match(/^\/(?!\/)/);
    return t ? tn(e, t) : n;
  }
}
function lh(e, t, n, r, i, a) {
  let o = e.xhr,
    s = o?.[Sp];
  if (!o || o.__sentry_own_request__ || !s) return;
  let { url: c, method: l } = s,
    u = H() && t(c);
  if (e.endTimestamp && u) {
    let t = o.__sentry_xhr_span_id__;
    if (!t) return;
    let n = r[t];
    n &&
      s.status_code !== void 0 &&
      (dr(n, s.status_code),
      n.end(),
      a?.(n, { headers: ih(wp(o)), error: e.error }),
      delete r[t]);
    return;
  }
  let d = nh(c),
    f = tc(d || c),
    p = nc(c),
    m = !!B(),
    h =
      u && m
        ? Ca({
            name: `${l} ${p}`,
            attributes: {
              url: c,
              type: `xhr`,
              "http.method": l,
              "http.url": d,
              "server.address": f?.host,
              [L]: `auto.http.browser`,
              [tr]: `http.client`,
              ...(f?.search && { "http.query": f?.search }),
              ...(f?.hash && { "http.fragment": f?.hash }),
            },
          })
        : new ki();
  ((o.__sentry_xhr_span_id__ = h.spanContext().spanId),
    (r[o.__sentry_xhr_span_id__] = h),
    n(c) && uh(o, H() && m ? h : void 0, i));
  let g = P();
  return (g && g.emit(`beforeOutgoingRequestSpan`, h, e), h);
}
function uh(e, t, n) {
  let {
    "sentry-trace": r,
    baggage: i,
    traceparent: a,
  } = lc({ span: t, propagateTraceparent: n });
  r && dh(e, r, i, a);
}
function dh(e, t, n, r) {
  let i = e.__sentry_xhr_v3__?.request_headers;
  if (!(i?.[`sentry-trace`] || !e.setRequestHeader))
    try {
      if (
        (e.setRequestHeader(`sentry-trace`, t),
        r && !i?.traceparent && e.setRequestHeader(`traceparent`, r),
        n)
      ) {
        let t = i?.baggage;
        (!t || !th(t)) && e.setRequestHeader(`baggage`, n);
      }
    } catch {}
}
var fh,
  ph,
  mh,
  hh = e(() => {
    (U(),
      Ip(),
      ah(),
      (fh = new WeakMap()),
      (ph = new Map()),
      (mh = {
        traceFetch: !0,
        traceXHR: !0,
        enableHTTPTimings: !0,
        trackFetchStreamPerformance: !1,
      }));
  });
function gh() {
  W.document
    ? W.document.addEventListener(`visibilitychange`, () => {
        let e = B();
        if (!e) return;
        let t = z(e);
        if (W.document.hidden && t) {
          let e = `cancelled`,
            { op: n, status: r } = R(t);
          (Q &&
            S.log(
              `[Tracing] Transaction: ${e} -> since tab moved to the background, op: ${n}`,
            ),
            r || t.setStatus({ code: 2, message: e }),
            t.setAttribute(`sentry.cancellation_reason`, `document.hidden`),
            t.end());
        }
      })
    : Q &&
      S.warn(
        `[Tracing] Could not set up background tab detection due to lack of global document`,
      );
}
var _h = e(() => {
  (U(), Bp(), G());
});
function vh(e, { linkPreviousTrace: t, consistentTraceSampling: n }) {
  let r = t === `session-storage`,
    i = r ? xh() : void 0;
  e.on(`spanStart`, (e) => {
    if (z(e) !== e) return;
    let t = M().getPropagationContext();
    ((i = yh(i, e, t)), r && bh(i));
  });
  let a = !0;
  n &&
    e.on(`beforeSampling`, (e) => {
      if (!i) return;
      let t = M(),
        n = t.getPropagationContext();
      if (a && n.parentSpanId) {
        a = !1;
        return;
      }
      (t.setPropagationContext({
        ...n,
        dsc: {
          ...n.dsc,
          sample_rate: String(i.sampleRate),
          sampled: String(Sh(i.spanContext)),
        },
        sampleRand: i.sampleRand,
      }),
        (e.parentSampled = Sh(i.spanContext)),
        (e.parentSampleRate = i.sampleRate),
        (e.spanAttributes = { ...e.spanAttributes, [er]: i.sampleRate }));
    });
}
function yh(e, t, n) {
  let r = R(t);
  function i() {
    try {
      return (
        Number(n.dsc?.sample_rate) ?? Number(r.data?.[`sentry.sample_rate`])
      );
    } catch {
      return 0;
    }
  }
  let a = {
    spanContext: t.spanContext(),
    startTimestamp: r.start_timestamp,
    sampleRate: i(),
    sampleRand: n.sampleRand,
  };
  if (!e) return a;
  let o = e.spanContext;
  return o.traceId === r.trace_id
    ? e
    : (Date.now() / 1e3 - e.startTimestamp <= 3600 &&
        (Q &&
          S.log(
            `Adding previous_trace ${o} link to span ${{ op: r.op, ...t.spanContext() }}`,
          ),
        t.addLink({ context: o, attributes: { [cr]: `previous_trace` } }),
        t.setAttribute(wh, `${o.traceId}-${o.spanId}-${Sh(o) ? 1 : 0}`)),
      a);
}
function bh(e) {
  try {
    W.sessionStorage.setItem(Ch, JSON.stringify(e));
  } catch (e) {
    Q && S.warn(`Could not store previous trace in sessionStorage`, e);
  }
}
function xh() {
  try {
    let e = W.sessionStorage?.getItem(Ch);
    return JSON.parse(e);
  } catch {
    return;
  }
}
function Sh(e) {
  return e.traceFlags === 1;
}
var Ch,
  wh,
  Th = e(() => {
    (U(),
      Bp(),
      G(),
      (Ch = `sentry_previous_trace`),
      (wh = `sentry.previous_trace`));
  });
function Eh(e, t, n) {
  (e.emit(`startPageLoadSpan`, t, n), M().setTransactionName(t.name));
  let r = Ah(e);
  return (r && e.emit(`afterStartPageLoadSpan`, r), r);
}
function Dh(e, t, n) {
  let { url: r, isRedirect: i } = n || {};
  (e.emit(`beforeStartNavigationSpan`, t, { isRedirect: i }),
    e.emit(`startNavigationSpan`, t, { isRedirect: i }));
  let a = M();
  return (
    a.setTransactionName(t.name),
    r &&
      !i &&
      a.setSDKProcessingMetadata({ normalizedRequest: { ...tu(), url: r } }),
    Ah(e)
  );
}
function Oh(e) {
  return (
    W.document?.querySelector(`meta[name=${e}]`)?.getAttribute(`content`) ||
    void 0
  );
}
function kh(e, t, n, r, i) {
  let a = W.document,
    o;
  a &&
    addEventListener(
      `click`,
      () => {
        let a = `ui.action.click`,
          s = Ah(e);
        if (s) {
          let e = R(s).op;
          if ([`navigation`, `pageload`].includes(e)) {
            Q &&
              S.warn(
                `[Tracing] Did not create ${a} span because a pageload or navigation span is in progress.`,
              );
            return;
          }
        }
        if (
          ((o &&=
            (o.setAttribute(nr, `interactionInterrupted`), o.end(), void 0)),
          !i.name)
        ) {
          Q &&
            S.warn(
              `[Tracing] Did not create ${a} transaction because _latestRouteName is missing.`,
            );
          return;
        }
        o = Pa(
          { name: i.name, op: a, attributes: { [I]: i.source || `url` } },
          { idleTimeout: t, finalTimeout: n, childSpanTimeout: r },
        );
      },
      { capture: !0 },
    );
}
function Ah(e) {
  return e[Ih];
}
function jh(e, t) {
  O(e, Ih, t);
}
function Mh(e, t) {
  let n = R(e),
    r = mn();
  return !(r - n.start_timestamp > Lh || (t && r - t <= Lh));
}
var Nh,
  Ph,
  Fh,
  Ih,
  Lh,
  Rh = e(() => {
    (U(),
      Ip(),
      Bp(),
      G(),
      _h(),
      Th(),
      hh(),
      (Nh = `BrowserTracing`),
      (Ph = {
        ...Ia,
        instrumentNavigation: !0,
        instrumentPageLoad: !0,
        markBackgroundSpan: !0,
        enableLongTask: !0,
        enableLongAnimationFrame: !0,
        enableInp: !0,
        enableElementTiming: !0,
        ignoreResourceSpans: [],
        ignorePerformanceApiSpans: [],
        detectRedirects: !0,
        linkPreviousTrace: `in-memory`,
        consistentTraceSampling: !1,
        enableReportPageLoaded: !1,
        _experiments: {},
        ...mh,
      }),
      (Fh = (e = {}) => {
        let t = { name: void 0, source: void 0 },
          n = W.document,
          {
            enableInp: r,
            enableElementTiming: i,
            enableLongTask: a,
            enableLongAnimationFrame: o,
            _experiments: {
              enableInteractions: s,
              enableStandaloneClsSpans: c,
              enableStandaloneLcpSpans: l,
            },
            beforeStartSpan: u,
            idleTimeout: d,
            finalTimeout: f,
            childSpanTimeout: p,
            markBackgroundSpan: m,
            traceFetch: h,
            traceXHR: g,
            trackFetchStreamPerformance: ee,
            shouldCreateSpanForRequest: te,
            enableHTTPTimings: ne,
            ignoreResourceSpans: re,
            ignorePerformanceApiSpans: ie,
            instrumentPageLoad: ae,
            instrumentNavigation: oe,
            detectRedirects: se,
            linkPreviousTrace: ce,
            consistentTraceSampling: le,
            enableReportPageLoaded: ue,
            onRequestSpanStart: de,
            onRequestSpanEnd: fe,
          } = { ...Ph, ...e },
          pe,
          me,
          he;
        function ge(e, r, i = !0) {
          let a = r.op === `pageload`,
            o = r.name,
            s = u ? u(r) : r,
            m = s.attributes || {};
          if ((o !== s.name && ((m[I] = `custom`), (s.attributes = m)), !i)) {
            let e = mn();
            Ca({ ...s, startTime: e }).end(e);
            return;
          }
          ((t.name = s.name), (t.source = m[I]));
          let h = Pa(s, {
            idleTimeout: d,
            finalTimeout: f,
            childSpanTimeout: p,
            disableAutoFinish: a,
            beforeSpanEnd: (t) => {
              (pe?.(),
                Nf(t, {
                  recordClsOnPageloadSpan: !c,
                  recordLcpOnPageloadSpan: !l,
                  ignoreResourceSpans: re,
                  ignorePerformanceApiSpans: ie,
                }),
                jh(e, void 0));
              let n = M(),
                r = n.getPropagationContext();
              (n.setPropagationContext({
                ...r,
                traceId: h.spanContext().traceId,
                sampled: ri(h),
                dsc: Ei(t),
              }),
                a && (he = void 0));
            },
            trimIdleSpanEndTimestamp: !ue,
          });
          (a && ue && (he = h), jh(e, h));
          function g() {
            n &&
              [`interactive`, `complete`].includes(n.readyState) &&
              e.emit(`idleSpanEnableAutoFinish`, h);
          }
          a &&
            !ue &&
            n &&
            (n.addEventListener(`readystatechange`, () => {
              g();
            }),
            g());
        }
        return {
          name: Nh,
          setup(e) {
            if (
              (fi(),
              (pe = Ef({
                recordClsStandaloneSpans: c || !1,
                recordLcpStandaloneSpans: l || !1,
                client: e,
              })),
              r && Ep(),
              i && Xf(),
              o &&
              y.PerformanceObserver &&
              PerformanceObserver.supportedEntryTypes &&
              PerformanceObserver.supportedEntryTypes.includes(
                `long-animation-frame`,
              )
                ? Of()
                : a && Df(),
              s && kf(),
              se && n)
            ) {
              let e = () => {
                me = A();
              };
              (addEventListener(`click`, e, { capture: !0 }),
                addEventListener(`keydown`, e, { capture: !0, passive: !0 }));
            }
            function t() {
              let t = Ah(e);
              t &&
                !R(t).timestamp &&
                (Q &&
                  S.log(
                    `[Tracing] Finishing current active span with op: ${R(t).op}`,
                  ),
                t.setAttribute(nr, `cancelled`),
                t.end());
            }
            (e.on(`startNavigationSpan`, (n, r) => {
              if (P() !== e) return;
              if (r?.isRedirect) {
                (Q &&
                  S.warn(
                    `[Tracing] Detected redirect, navigation span will not be the root span, but a child span.`,
                  ),
                  ge(e, { op: `navigation.redirect`, ...n }, !1));
                return;
              }
              ((me = void 0),
                t(),
                N().setPropagationContext({
                  traceId: On(),
                  sampleRand: Math.random(),
                  propagationSpanId: H() ? void 0 : kn(),
                }));
              let i = M();
              (i.setPropagationContext({
                traceId: On(),
                sampleRand: Math.random(),
                propagationSpanId: H() ? void 0 : kn(),
              }),
                i.setSDKProcessingMetadata({ normalizedRequest: void 0 }),
                ge(e, {
                  op: `navigation`,
                  ...n,
                  parentSpan: null,
                  forceTransaction: !0,
                }));
            }),
              e.on(`startPageLoadSpan`, (n, r = {}) => {
                if (P() !== e) return;
                t();
                let i = Hr(
                    r.sentryTrace || Oh(`sentry-trace`),
                    r.baggage || Oh(`baggage`),
                  ),
                  a = M();
                (a.setPropagationContext(i),
                  H() || (a.getPropagationContext().propagationSpanId = kn()),
                  a.setSDKProcessingMetadata({ normalizedRequest: tu() }),
                  ge(e, { op: `pageload`, ...n }));
              }),
              e.on(`endPageloadSpan`, () => {
                ue && he && (he.setAttribute(nr, `reportPageLoaded`), he.end());
              }));
          },
          afterAllSetup(e) {
            let n = Bt();
            if (
              (ce !== `off` &&
                vh(e, { linkPreviousTrace: ce, consistentTraceSampling: le }),
              W.location)
            ) {
              if (ae) {
                let t = j();
                Eh(e, {
                  name: W.location.pathname,
                  startTime: t ? t / 1e3 : void 0,
                  attributes: { [I]: `url`, [L]: `auto.pageload.browser` },
                });
              }
              oe &&
                up(({ to: t, from: r }) => {
                  if (r === void 0 && n?.indexOf(t) !== -1) {
                    n = void 0;
                    return;
                  }
                  n = void 0;
                  let i = $s(t),
                    a = Ah(e),
                    o = a && se && Mh(a, me);
                  Dh(
                    e,
                    {
                      name: i?.pathname || W.location.pathname,
                      attributes: {
                        [I]: `url`,
                        [L]: `auto.navigation.browser`,
                      },
                    },
                    { url: t, isRedirect: o },
                  );
                });
            }
            (m && gh(),
              s && kh(e, d, f, p, t),
              r && Op(),
              oh(e, {
                traceFetch: h,
                traceXHR: g,
                trackFetchStreamPerformance: ee,
                tracePropagationTargets: e.getOptions().tracePropagationTargets,
                shouldCreateSpanForRequest: te,
                enableHTTPTimings: ne,
                onRequestSpanStart: de,
                onRequestSpanEnd: fe,
              }));
          },
        };
      }),
      (Ih = `_sentry_idleSpan`),
      (Lh = 1.5));
  }),
  zh = e(() => {
    (U(), rm(), eh(), Rh());
  });
function Bh() {
  let e = Xn().getScopeData(),
    t = N().getScopeData(),
    n = M().getScopeData();
  return ($a(e, t), $a(e, n), (e.eventProcessors = []), e);
}
function Vh(e) {
  (N().addScopeListener((t) => {
    e(Bh(), t);
  }),
    M().addScopeListener((t) => {
      e(Bh(), t);
    }),
    Xn().addScopeListener((t) => {
      e(Bh(), t);
    }));
}
var Hh = e(() => {
    U();
  }),
  Uh,
  Wh = e(() => {
    (U(),
      Hh(),
      Yl(),
      (Uh = qo(() => ({
        name: `ScopeToMain`,
        setup(e) {
          let t = ql(e);
          Vh((e, n) => {
            (t.sendScope(JSON.stringify(ji(e, 20, 2e3))),
              n.clearBreadcrumbs(),
              n.clearAttachments());
          });
        },
      }))));
  }),
  Gh = e(() => {
    (U(), Yl());
  });
function Kh(e) {
  let t;
  return ys(
    e,
    async (e) => ((t ||= ql()), t.sendEnvelope(e.body), { statusCode: 200 }),
  );
}
var qh = e(() => {
    (U(), Yl());
  }),
  Jh,
  Yh,
  Xh,
  Zh,
  Qh = e(() => {
    (zh(),
      U(),
      (Jh = 50),
      ([, Yh] = Yp),
      ([, Xh] = zl()),
      (Zh = (e, t = 0) => {
        let n = [];
        for (let r of e
          .split(
            `
`,
          )
          .slice(t)) {
          let e = Yh(r),
            t = Xh(r);
          if (
            (e && t?.in_app !== !1
              ? n.push(e)
              : t && (t.module === void 0 && delete t.module, n.push(t)),
            n.length >= Jh)
          )
            break;
        }
        return Qe(n);
      }));
  });
function $h(e) {
  return [...Qm(e).filter((e) => e.name !== `BrowserSession`), Uh()];
}
function eg(e = {}, t = $m) {
  if (window?.__SENTRY__RENDERER_INIT__) {
    S.warn(`The browser SDK has already been initialized.
If init has been called in the preload and contextIsolation is disabled, is not required to call init in the renderer`);
    return;
  }
  ((window.__SENTRY__RENDERER_INIT__ = !0),
    (e.sendClientReports = !1),
    e.defaultIntegrations === void 0 && (e.defaultIntegrations = $h(e)),
    e.stackParser === void 0 && (e.stackParser = Zh),
    e.ipcNamespace === void 0 && (e.ipcNamespace = `sentry-ipc`),
    e.dsn === void 0 && (e.dsn = `https://12345@dummy.dsn/12345`),
    e.transport === void 0 && (e.transport = Kh),
    delete e.initialScope,
    t(e));
}
var tg = e(() => {
    (zh(), U(), Wh(), Qh(), qh());
  }),
  ng = e(() => {
    (Xl(), Zl(), zh(), Wh(), Gh(), qh(), Qh(), tg());
  });
function rg() {
  if (sg) return;
  sg = !0;
  let e = window.electronBridge?.getSentryInitOptions?.();
  if (!e) return;
  let t = d(e.buildFlavor),
    n = re(e.appVersion),
    r = e.buildFlavor !== `prod`;
  (eg({
    enabled: !1,
    beforeSend: m,
    dsn: oe,
    environment: cg,
    release: p(n.version),
    dist: e.buildNumber ?? void 0,
    tracesSampleRate: 0,
    integrations: (e) => (t ? [...e, Fh()] : e),
  }),
    Oo(`sessionId`, e.codexAppSessionId),
    Oo(`buildFlavor`, e.buildFlavor),
    Oo(`preRelease`, r),
    Oo(`bundle`, `webview`),
    Oo(`host`, `app`),
    c(og));
}
function ig(e) {
  ko(
    e.authMethod == null
      ? null
      : {
          id: e.userId ?? void 0,
          authMethod: e.authMethod,
          account_id: e.accountId ?? void 0,
        },
  );
}
function ag(e, t) {
  try {
    let n = f(e, t);
    return Eo(n.error, n);
  } catch {
    return ``;
  }
}
function og(e, t) {
  try {
    return Eo(e, t);
  } catch {
    return ``;
  }
}
var sg,
  cg,
  lg = e(() => {
    (ng(), g(), h(), (sg = !1), (cg = `prod`));
  }),
  ug,
  dg,
  fg = e(() => {
    (g(), (ug = `chatgpt`), (dg = ie(ug)));
  });
function pg(e) {
  switch (e) {
    case `ready`:
    case `downloading`:
    case `installing`:
      return !0;
    case `checking`:
    case `idle`:
      return !1;
  }
}
var mg,
  hg,
  gg,
  _g,
  vg,
  yg,
  bg,
  xg = e(() => {
    (fe(),
      n(),
      (mg = {
        downloadProgressPercent: null,
        downloadedUpdateAppBrand: null,
        installProgressPercent: null,
        isUpdateReady: !1,
        lifecycleState: `idle`,
        relaunchNotice: null,
      }),
      (hg = l(se, mg)),
      (gg = ve(se, ({ get: e }) => e(hg).isUpdateReady)),
      (_g = ve(se, ({ get: e }) => e(hg).lifecycleState)),
      (vg = ve(se, ({ get: e }) => e(hg).downloadProgressPercent)),
      (yg = ve(se, ({ get: e }) => e(hg).installProgressPercent)),
      (bg = ve(se, ({ get: e }) => e(hg).relaunchNotice)));
  });
function Sg(e) {
  let t = (0, wg.c)(11),
    { appUpdateLifecycleState: n } = e === void 0 ? {} : e,
    { platform: r } = ke(),
    i = ce(_g);
  switch (n ?? i) {
    case `idle`: {
      let e;
      t[0] === r
        ? (e = t[1])
        : ((e = () => {
            if (r === `macOS`) {
              le.appUpdates?.installUpdate();
              return;
            }
            le.appUpdates?.checkForUpdates();
          }),
          (t[0] = r),
          (t[1] = e));
      let n;
      t[2] === Symbol.for(`react.memo_cache_sentinel`)
        ? ((n = (0, $.jsx)(we, { className: `icon-xs` })), (t[2] = n))
        : (n = t[2]);
      let i;
      t[3] === Symbol.for(`react.memo_cache_sentinel`)
        ? ((i = (0, $.jsx)(ye, {
            id: `appUpdate.recovery.updateCodex`,
            defaultMessage: `Update {appName}`,
            description: `Button label shown on the full-screen error page to install a desktop app update`,
            values: { appName: dg },
          })),
          (t[3] = i))
        : (i = t[3]);
      let a;
      return (
        t[4] === e
          ? (a = t[5])
          : ((a = (0, $.jsxs)(ae, { onClick: e, children: [n, i] })),
            (t[4] = e),
            (t[5] = a)),
        a
      );
    }
    case `ready`: {
      let e;
      t[6] === Symbol.for(`react.memo_cache_sentinel`)
        ? ((e = (0, $.jsx)(we, { className: `icon-xs` })), (t[6] = e))
        : (e = t[6]);
      let n;
      return (
        t[7] === Symbol.for(`react.memo_cache_sentinel`)
          ? ((n = (0, $.jsxs)(ae, {
              onClick: Cg,
              children: [
                e,
                (0, $.jsx)(ye, {
                  id: `appUpdate.recovery.updateCodex`,
                  defaultMessage: `Update {appName}`,
                  description: `Button label shown on the full-screen error page to install a desktop app update`,
                  values: { appName: dg },
                }),
              ],
            })),
            (t[7] = n))
          : (n = t[7]),
        n
      );
    }
    case `checking`: {
      let e;
      return (
        t[8] === Symbol.for(`react.memo_cache_sentinel`)
          ? ((e = (0, $.jsxs)(ae, {
              color: `outline`,
              disabled: !0,
              children: [
                (0, $.jsx)(ue, { className: `icon-xs` }),
                (0, $.jsx)(ye, {
                  id: `appUpdate.recovery.checkingForUpdates`,
                  defaultMessage: `Checking for updates`,
                  description: `Disabled status shown on the full-screen error page while checking for desktop app updates`,
                }),
              ],
            })),
            (t[8] = e))
          : (e = t[8]),
        e
      );
    }
    case `downloading`: {
      let e;
      return (
        t[9] === Symbol.for(`react.memo_cache_sentinel`)
          ? ((e = (0, $.jsxs)(ae, {
              color: `outline`,
              disabled: !0,
              children: [
                (0, $.jsx)(ue, { className: `icon-xs` }),
                (0, $.jsx)(ye, {
                  id: `appUpdate.recovery.downloadingUpdate`,
                  defaultMessage: `Downloading update`,
                  description: `Disabled status shown on the full-screen error page while downloading a desktop app update`,
                }),
              ],
            })),
            (t[9] = e))
          : (e = t[9]),
        e
      );
    }
    case `installing`: {
      let e;
      return (
        t[10] === Symbol.for(`react.memo_cache_sentinel`)
          ? ((e = (0, $.jsxs)(ae, {
              color: `outline`,
              disabled: !0,
              children: [
                (0, $.jsx)(ue, { className: `icon-xs` }),
                (0, $.jsx)(ye, {
                  id: `appUpdate.recovery.installingUpdate`,
                  defaultMessage: `Installing update`,
                  description: `Disabled status shown on the full-screen error page while installing a desktop app update`,
                }),
              ],
            })),
            (t[10] = e))
          : (e = t[10]),
        e
      );
    }
  }
}
function Cg() {
  le.appUpdates?.installUpdate();
}
var wg,
  $,
  Tg = e(() => {
    ((wg = ee()),
      fe(),
      s(),
      fg(),
      xg(),
      me(),
      de(),
      Me(),
      Te(),
      he(),
      ($ = ge()));
  });
function Eg(e) {
  return Object.prototype.toString.call(e);
}
function Dg(e, t) {
  try {
    return e instanceof t;
  } catch {
    return !1;
  }
}
function Og(e) {
  switch (Eg(e)) {
    case `[object Error]`:
    case `[object Exception]`:
    case `[object DOMException]`:
      return !0;
    default:
      return Dg(e, Error);
  }
}
function kg(e, t) {
  let n = new WeakMap();
  function r(e, t) {
    if (!n.has(e)) {
      if (e.cause) return (n.set(e, !0), r(e.cause, t));
      e.cause = t;
    }
  }
  r(e, t);
}
function Ag(e) {
  let t = (0, jg.c)(9),
    { resetError: n } = e,
    r = _e(),
    i,
    a;
  t[0] === Symbol.for(`react.memo_cache_sentinel`)
    ? ((i = (0, Ng.jsx)(Se, {
        className: `icon-lg text-token-error-foreground`,
      })),
      (a = (0, Ng.jsx)(ye, {
        id: `codex.errorBoundary.genericError`,
        defaultMessage: `Oops, an error has occurred`,
        description: `Generic error message shown when the extension webview fails`,
      })),
      (t[0] = i),
      (t[1] = a))
    : ((i = t[0]), (a = t[1]));
  let o;
  t[2] === Symbol.for(`react.memo_cache_sentinel`)
    ? ((o = (0, Ng.jsx)(xe, { electron: !0, children: (0, Ng.jsx)(Sg, {}) })),
      (t[2] = o))
    : (o = t[2]);
  let s;
  t[3] !== r || t[4] !== n
    ? ((s = () => {
        (n(), r(`/`));
      }),
      (t[3] = r),
      (t[4] = n),
      (t[5] = s))
    : (s = t[5]);
  let c;
  t[6] === Symbol.for(`react.memo_cache_sentinel`)
    ? ((c = (0, Ng.jsx)(ye, {
        id: `codex.errorBoundary.goHome`,
        defaultMessage: `Try again`,
        description: `Button label to navigate to the home page after an error`,
      })),
      (t[6] = c))
    : (c = t[6]);
  let l;
  return (
    t[7] === s
      ? (l = t[8])
      : ((l = (0, Ng.jsxs)(`div`, {
          className: `flex h-full flex-col items-center justify-center gap-4`,
          children: [
            i,
            a,
            (0, Ng.jsxs)(`div`, {
              className: `flex flex-wrap items-center justify-center gap-2`,
              children: [o, (0, Ng.jsx)(ae, { onClick: s, children: c })],
            }),
          ],
        })),
        (t[7] = s),
        (t[8] = l)),
    l
  );
}
var jg,
  Mg,
  Ng,
  Pg,
  Fg,
  Ig = e(() => {
    ((jg = ee()),
      (Mg = t(r(), 1)),
      s(),
      pe(),
      Ce(),
      lg(),
      ne(),
      Tg(),
      me(),
      be(),
      (Ng = ge()),
      (Pg = { componentStack: null, error: null, eventId: `` }),
      (Fg = class extends Mg.Component {
        state = Pg;
        componentDidUpdate(e) {
          this.state.error == null ||
            e.resetKey === this.props.resetKey ||
            this.resetErrorBoundary();
        }
        componentDidCatch(e, { componentStack: t }) {
          let n = t ?? ``,
            r = Og(e) ? e : Error(String(e));
          if (Og(e)) {
            let t = Error(e.message);
            ((t.name = `React ErrorBoundary ${t.name}`),
              (t.stack = n),
              kg(e, t));
          }
          let i = ag(r, { boundaryName: this.props.name, componentStack: n });
          this.props.onError && this.props.onError(r, n, i);
          try {
            a.error(`error boundary`, {
              safe: { name: this.props.name },
              sensitive: { error: e, componentStack: t ?? `` },
            });
          } catch {}
          this.setState({ error: r, componentStack: t, eventId: i });
        }
        resetErrorBoundary = () => {
          let { onReset: e } = this.props,
            { error: t, componentStack: n, eventId: r } = this.state;
          (e && e(t, n ?? ``, r), this.setState(Pg));
        };
        render() {
          let { fallback: e, children: t } = this.props,
            n = this.state,
            r =
              e ??
              ((e) => (0, Ng.jsx)(Ag, { resetError: () => e.resetError() }));
          if (n.error) {
            let e;
            return (
              (e =
                typeof r == `function`
                  ? r({
                      error: n.error,
                      componentStack: n.componentStack ?? ``,
                      eventId: n.eventId,
                      resetError: this.resetErrorBoundary,
                    })
                  : r),
              (0, Mg.isValidElement)(e) ? e : null
            );
          }
          return typeof t == `function` ? t() : t;
        }
      }));
  });
export {
  Ee as S,
  lg as _,
  vg as a,
  ke as b,
  gg as c,
  xg as d,
  pg as f,
  rg as g,
  fg as h,
  Tg as i,
  bg as l,
  dg as m,
  Ig as n,
  yg as o,
  ug as p,
  Sg as r,
  _g as s,
  Fg as t,
  hg as u,
  ig as v,
  Oe as x,
  Me as y,
};
//# sourceMappingURL=app-initial~artifact-tab-content.electron~notebook-preview-panel~app-main~appgen-settings-p~k7s99xf2-CDccaBxU.js.map
