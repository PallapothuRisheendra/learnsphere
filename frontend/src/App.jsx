
import { useEffect, useMemo, useRef, useState } from "react";
import "./App.css";

const API_BASE_URL = (import.meta.env.VITE_API_URL || "").replace(/\/+$/, "");
let csrfToken = "";

async function apiRequest(path, options = {}) {
  let response;
  try {
    const method = (options.method || "GET").toUpperCase();
    const headers = new Headers(options.headers || {});
    if (!csrfToken && !["GET", "HEAD", "OPTIONS"].includes(method) && path !== "/api/auth/csrf") {
      const csrf = await fetch(`${API_BASE_URL}/api/auth/csrf`, { credentials: "include" });
      if (csrf.ok) csrfToken = (await csrf.json()).token || "";
    }
    if (csrfToken && !["GET", "HEAD", "OPTIONS"].includes(method)) headers.set("X-XSRF-TOKEN", csrfToken);
    response = await fetch(`${API_BASE_URL}${path}`, { ...options, headers, credentials: "include" });
  } catch {
    throw new Error("The learning server is unavailable. Check your connection and try again.");
  }

  const contentType = response.headers.get("content-type") || "";
  let data = null;
  if (contentType.includes("/json") || contentType.includes("+json")) {
    try { data = await response.json(); } catch { data = null; }
  } else {
    try { data = await response.text(); } catch { data = null; }
  }

  if (!response.ok) {
    const fieldErrors = data?.fields ? Object.values(data.fields).join(" ") : "";
    const error = new Error(fieldErrors && data?.message
      ? `${data.message} ${fieldErrors}`
      : data?.message || fieldErrors || `Request failed (${response.status}).`);
    error.status = response.status;
    throw error;
  }
  return data;
}

const filters = ["All courses", "Programming", "Development", "Computer Science"];

function App() {
  const [courses, setCourses] = useState([]);
  const [user, setUser] = useState(null);
  const [authMode, setAuthMode] = useState("login");
  const [authName, setAuthName] = useState("");
  const [authEmail, setAuthEmail] = useState("");
  const [authPassword, setAuthPassword] = useState("");
  const [authError, setAuthError] = useState("");
  const [authMessage, setAuthMessage] = useState("");
  const [authBusy, setAuthBusy] = useState(false);
  const [authChecking, setAuthChecking] = useState(true);
  const [activeCourse, setActiveCourse] = useState(null);
  const [lessons, setLessons] = useState([]);
  const [activeLesson, setActiveLesson] = useState(0);
  const [lessonBusy, setLessonBusy] = useState(false);
  const [search, setSearch] = useState("");
  const [activeFilter, setActiveFilter] = useState("All courses");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState(null);
  const [openingCourseId, setOpeningCourseId] = useState(null);
  const [showNotifications, setShowNotifications] = useState(false);
  const [showLearningPaths, setShowLearningPaths] = useState(false);
  const [showMobileMenu, setShowMobileMenu] = useState(false);
  const [courseView, setCourseView] = useState("all");
  const [pageInfo, setPageInfo] = useState({ page: 0, size: 9, totalElements: 0, totalPages: 0 });
  const [levelFilter, setLevelFilter] = useState("");
  const [catalogSort, setCatalogSort] = useState("name");
  const [showAdmin, setShowAdmin] = useState(false);
  const [adminTab, setAdminTab] = useState("courses");
  const [adminItems, setAdminItems] = useState([]);
  const [adminBusy, setAdminBusy] = useState(false);
  const [adminError, setAdminError] = useState("");
  const [adminCourseId, setAdminCourseId] = useState(null);
  const [editingCourseId, setEditingCourseId] = useState(null);
  const [adminLessons, setAdminLessons] = useState([]);
  const [courseDraft, setCourseDraft] = useState({name:"",category:"Programming",level:"Beginner",duration:"4 weeks",description:"",icon:"",color:"violet",priceMinor:0,currency:"INR",thumbnailUrl:""});
  const [lessonDraft, setLessonDraft] = useState({title:"",description:"",content:"",sortOrder:1,videoUrl:""});
  const [editingLessonId, setEditingLessonId] = useState(null);
  const searchInputRef = useRef(null);
  const dialogOpen = Boolean(activeCourse) || showLearningPaths || showAdmin;

  useEffect(() => {
    if (!dialogOpen) return undefined;
    const previousFocus = document.activeElement;
    const selector = "button:not([disabled]), a[href], input:not([disabled]), [tabindex]:not([tabindex='-1'])";
    const dialog = document.querySelector("[role='dialog']");
    const focusable = dialog?.querySelectorAll(selector);
    focusable?.[0]?.focus();

    function keepFocusInDialog(event) {
      if (event.key === "Escape") {
        setActiveCourse(null);
        setShowLearningPaths(false);
        setShowAdmin(false);
        return;
      }
      if (event.key !== "Tab" || !dialog) return;
      const items = [...dialog.querySelectorAll(selector)];
      const first = items[0];
      const last = items[items.length - 1];
      if (!first || !last) return;
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      }
    }

    document.addEventListener("keydown", keepFocusInDialog);
    return () => {
      document.removeEventListener("keydown", keepFocusInDialog);
      if (previousFocus instanceof HTMLElement) previousFocus.focus();
    };
  }, [dialogOpen]);

  function clearSession() {
    setAuthMode("login");
    setUser(null);
    setActiveCourse(null);
    setLessons([]);
    setError("");
    setNotice(null);
    setShowMobileMenu(false);
  }

  useEffect(() => {
    let cancelled = false;
    setAuthChecking(true);
    apiRequest("/api/auth/csrf").then((data) => { csrfToken = data?.token || ""; })
      .then(() => apiRequest("/api/auth/me"))
      .then((currentUser) => {
        if (cancelled) return;
        setUser(currentUser);
      })
      .catch((requestError) => {
        if (cancelled) return;
        if (requestError.status !== 401) {
          setError(requestError.message);
        }
      })
      .finally(() => { if (!cancelled) setAuthChecking(false); });
    return () => { cancelled = true; };
  }, []);

  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const verifyToken = params.get("verify");
    const resetToken = params.get("reset");
    if (resetToken) { setAuthMode("reset"); return; }
    if (!verifyToken) return;
    apiRequest("/api/auth/verify-email", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ token: verifyToken }) })
      .then((result) => { setUser(result.user); setAuthMessage(result.message || "Your email is verified."); window.history.replaceState({}, "", window.location.pathname); })
      .catch((requestError) => setAuthError(requestError.message))
      .finally(() => setAuthChecking(false));
  }, []);

  useEffect(() => {
    function handleSearchShortcut(event) {
      if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === "k") {
        event.preventDefault();
        searchInputRef.current?.focus();
      }
    }
    window.addEventListener("keydown", handleSearchShortcut);
    return () => window.removeEventListener("keydown", handleSearchShortcut);
  }, [searchInputRef]);

  useEffect(() => {
    if (!user || authChecking) {
      setLoading(false);
      if (!user) setCourses([]);
      return undefined;
    }
    let cancelled = false;

    async function loadCourses() {
      try {
        setLoading(true);
        setError("");

        const params = new URLSearchParams({ page: String(pageInfo.page), size: String(pageInfo.size), search: search.trim(),
          category: activeFilter === "All courses" ? "" : activeFilter, level: levelFilter, enrolledOnly: String(courseView === "enrolled"), sort: catalogSort, direction: "asc" });
        const data = await apiRequest(`/api/courses?${params}`);
        const list = data?.content;
        const textFields = ["name", "category", "level", "duration", "description", "icon", "color"];
        if (!Array.isArray(list) || list.some((course) => !course || typeof course !== "object"
          || !Number.isInteger(course.id) || textFields.some((field) => typeof course[field] !== "string")
          || !Number.isInteger(course.progress) || course.progress < 0 || course.progress > 100
          || typeof course.enrolled !== "boolean")) {
          throw new Error("The server returned an invalid course list.");
        }

        if (!cancelled) {
          setCourses(list);
          setPageInfo((current) => ({ ...current, page: data.page, size: data.size, totalElements: data.totalElements, totalPages: data.totalPages }));
        }
      } catch (requestError) {
        if (!cancelled) {
          if (requestError.status === 401) {
            clearSession();
          }
          setError(requestError.message || "Unable to load courses.");
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    }

    loadCourses();

    return () => {
      cancelled = true;
    };
  }, [user, authChecking, pageInfo.page, pageInfo.size, search, activeFilter, courseView, levelFilter, catalogSort]);

  async function handleAuthSubmit(event) {
    event.preventDefault();
    setAuthBusy(true);
    setAuthError("");
    try {
      const endpoint = authMode === "register" ? "register" : authMode === "forgot" ? "forgot-password" : authMode === "reset" ? "reset-password" : "login";
      if (authMode === "forgot") {
        const result = await apiRequest(`/api/auth/${endpoint}`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ email: authEmail.trim() }) });
        setAuthMessage(result.message || "If an account matches, a reset link will be sent.");
        return;
      }
      if (authMode === "reset") {
        const resetToken = new URLSearchParams(window.location.search).get("reset");
        if (!resetToken) throw new Error("This reset link is invalid. Request a new password reset email.");
        const result = await apiRequest(`/api/auth/${endpoint}`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ token: resetToken, password: authPassword }) });
        setAuthMessage(result.message || "Password updated. Sign in with your new password.");
        setAuthMode("login"); setAuthPassword(""); window.history.replaceState({}, "", window.location.pathname);
        return;
      }
      if (authMode === "register" && new TextEncoder().encode(authPassword).length > 72) {
        setAuthError("Password must not exceed 72 UTF-8 bytes.");
        return;
      }
      const payload = authMode === "register"
        ? { fullName: authName, email: authEmail, password: authPassword }
        : { email: authEmail, password: authPassword };
      const data = await apiRequest(`/api/auth/${endpoint}`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ ...payload, email: payload.email.trim(), ...(payload.fullName ? { fullName: payload.fullName.trim() } : {}) }),
      });
      if (data?.verificationRequired) {
        setAuthMessage(data.message || "Check your email to verify your account before signing in.");
        setAuthPassword("");
        return;
      }
      if (!data?.user) throw new Error("The server returned an incomplete sign-in response.");
      setNotice({ type: "success", message: authMode === "register" ? "Your account is ready." : "You’re signed in." });
      setUser(data.user);
      setAuthPassword("");
    } catch (requestError) {
      setAuthError(requestError.message || "The learning server is unavailable.");
    } finally {
      setAuthBusy(false);
    }
  }

  async function handleLogout() {
    try { await apiRequest("/api/auth/logout", { method: "POST" }); }
    finally { csrfToken = ""; clearSession(); }
  }

  async function handleCourseAction(course) {
    if (openingCourseId !== null) return;
    setOpeningCourseId(course.id);
    setError("");
    setNotice(null);
    try {
      let enrolledCourse = course;
      if (!course.enrolled) {
        if (Number(course.priceMinor) > 0) {
          const order = await apiRequest(`/api/payments/courses/${course.id}/order`, { method: "POST" });
          if (!window.Razorpay) {
            await new Promise((resolve, reject) => {
              const script = document.createElement("script"); script.src = "https://checkout.razorpay.com/v1/checkout.js";
              script.onload = resolve; script.onerror = () => reject(new Error("Could not load Razorpay Checkout.")); document.body.appendChild(script);
            });
          }
          const checkout = new window.Razorpay({ key: order.keyId, order_id: order.orderId, amount: order.amount, currency: order.currency,
            name: "LearnSphere", description: course.name, handler: async (result) => {
              try {
                await apiRequest("/api/payments/verify", { method: "POST", headers: { "Content-Type": "application/json" },
                  body: JSON.stringify({ orderId: result.razorpay_order_id, paymentId: result.razorpay_payment_id, signature: result.razorpay_signature }) });
                const paidCourse = { ...course, enrolled: true };
                setCourses((current) => current.map((item) => item.id === course.id ? paidCourse : item));
                const paidLessons = await apiRequest(`/api/courses/${course.id}/lessons`);
                setActiveCourse(paidCourse); setLessons(paidLessons); setActiveLesson(Math.max(0, paidLessons.findIndex((lesson) => !lesson.completed)));
                setNotice({ type: "success", message: `Payment confirmed. You’re enrolled in ${course.name}.` });
              } catch (paymentError) { setNotice({ type: "error", message: paymentError.message || "Payment could not be verified." }); }
              finally { setOpeningCourseId(null); }
            }, modal: { ondismiss: () => setOpeningCourseId(null) } });
          checkout.open();
          return;
        }
        enrolledCourse = await apiRequest(`/api/courses/${course.id}/enroll`, { method: "POST" });
        if (!enrolledCourse || enrolledCourse.id !== course.id || typeof enrolledCourse.enrolled !== "boolean") {
          throw new Error("The server returned an invalid enrollment response.");
        }
        setCourses((current) => current.map((item) => item.id === enrolledCourse.id ? enrolledCourse : item));
        setNotice({ type: "success", message: `You’re enrolled in ${course.name}.` });
      }
      const data = await apiRequest(`/api/courses/${course.id}/lessons`);
      if (!Array.isArray(data) || data.some((lesson) => !lesson || !Number.isInteger(lesson.id)
        || typeof lesson.title !== "string" || typeof lesson.description !== "string"
        || typeof lesson.content !== "string" || typeof lesson.completed !== "boolean")) {
        throw new Error("The server returned an invalid lesson list.");
      }
      setCourses((current) => current.map((item) => item.id === enrolledCourse.id ? enrolledCourse : item));
      setActiveCourse(enrolledCourse);
      setLessons(data);
      const firstIncomplete = data.findIndex((lesson) => !lesson.completed);
      setActiveLesson(firstIncomplete < 0 ? 0 : firstIncomplete);
    } catch (requestError) {
      if (requestError.status === 401) clearSession();
      setNotice({ type: "error", message: requestError.message || "Unable to open this course." });
    } finally {
      setOpeningCourseId(null);
    }
  }

  async function completeActiveLesson() {
    const lesson = lessons[activeLesson];
    if (!lesson || lesson.completed || lessonBusy) return;
    setLessonBusy(true);
    setNotice(null);
    try {
      const updated = await apiRequest(`/api/courses/${activeCourse.id}/lessons/${lesson.id}/complete`, { method: "POST" });
      if (!updated || updated.id !== lesson.id || updated.completed !== true
        || !Number.isInteger(updated.courseProgress) || updated.courseProgress < 0 || updated.courseProgress > 100) {
        throw new Error("The server returned an invalid lesson progress response.");
      }
      setError("");
      setNotice({ type: "success", message: "Lesson progress saved." });
      setLessons((current) => current.map((item) => item.id === updated.id ? updated : item));
      setCourses((current) => current.map((item) => item.id === activeCourse.id
        ? { ...item, progress: updated.courseProgress, enrolled: true }
        : item));
      setActiveCourse((current) => ({ ...current, progress: updated.courseProgress, enrolled: true }));
    } catch (requestError) {
      if (requestError.status === 401) clearSession();
      setNotice({ type: "error", message: requestError.message || "Unable to save lesson progress." });
    } finally {
      setLessonBusy(false);
    }
  }

  async function loadAdminItems(tab = adminTab) {
    setAdminBusy(true); setAdminError("");
    try {
      const result = await apiRequest(`/api/admin/${tab}`);
      setAdminItems(tab === "courses" ? (result?.content || []) : result);
    } catch (error) { setAdminError(error.message); }
    finally { setAdminBusy(false); }
  }
  async function saveAdminCourse(event) {
    event.preventDefault(); setAdminBusy(true); setAdminError("");
    try {
      await apiRequest(editingCourseId ? `/api/admin/courses/${editingCourseId}` : "/api/admin/courses", {method:editingCourseId?"PUT":"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({...courseDraft,priceMinor:Number(courseDraft.priceMinor)})});
      setEditingCourseId(null); setCourseDraft({name:"",category:"Programming",level:"Beginner",duration:"4 weeks",description:"",icon:"",color:"violet",priceMinor:0,currency:"INR"});
      await loadAdminItems("courses");
    } catch (error) { setAdminError(error.message); } finally { setAdminBusy(false); }
  }
  async function deleteAdminItem(item) {
    const route = adminTab === "courses" ? `courses/${item.id}` : adminTab === "users" ? `users/${item.id}` : `enrollments/${item.id}`;
    try { await apiRequest(`/api/admin/${route}`, {method:"DELETE"}); await loadAdminItems(); }
    catch (error) { setAdminError(error.message); }
  }
  async function openAdminLessons(course) {
    setAdminCourseId(course.id);
    try { setAdminLessons(await apiRequest(`/api/admin/courses/${course.id}/lessons`)); }
    catch (error) { setAdminError(error.message); }
  }
  async function saveAdminLesson(event) {
    event.preventDefault();
    try { await apiRequest(`/api/admin/courses/${adminCourseId}/lessons${editingLessonId?`/${editingLessonId}`:""}`,{method:editingLessonId?"PUT":"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({...lessonDraft,sortOrder:Number(lessonDraft.sortOrder)})});
      setEditingLessonId(null);
      setLessonDraft({title:"",description:"",content:"",sortOrder:adminLessons.length+2,videoUrl:""});
      setAdminLessons(await apiRequest(`/api/admin/courses/${adminCourseId}/lessons`));
    } catch(error) {setAdminError(error.message);}
  }

  const allCourses = useMemo(() => {
    return courses;
  }, [courses]);

  const filteredCourses = allCourses;

  const enrolledCourses = allCourses.filter(
    (course) => course.enrolled
  );
  const ongoingCourses = enrolledCourses.filter((course) => course.progress < 100);

  const completedCourses = allCourses.filter(
    (course) => course.progress === 100
  );

  const averageProgress =
    enrolledCourses.length > 0
      ? Math.round(
          enrolledCourses.reduce(
            (total, course) => total + course.progress,
            0
          ) / enrolledCourses.length
        )
      : 0;

  if (!user && !authChecking) {
    return (
      <div className="ls-auth-page">
        <a className="ls-brand ls-auth-brand" href="/">
          <span className="ls-brand-icon">L</span>
          <span>learn<span className="ls-brand-light">sphere</span></span>
        </a>
        <form className="ls-auth-card" onSubmit={handleAuthSubmit}>
          <div className="ls-eyebrow"><span className="ls-eyebrow-dot"></span> YOUR PERSONAL LEARNING SPACE</div>
          <h1>{authMode === "register" ? "Create your account." : authMode === "forgot" ? "Reset your password." : authMode === "reset" ? "Choose a new password." : "Welcome back."}</h1>
          <p>{authMode === "register" ? "Start building skills, one course at a time." : authMode === "forgot" ? "We’ll email a secure reset link if the account exists." : authMode === "reset" ? "Choose a strong password for your account." : "Sign in to continue your learning journey."}</p>
          {authMode === "register" && (
            <label>Full name<input autoComplete="name" value={authName} onChange={(event) => setAuthName(event.target.value)} required maxLength={100} /></label>
          )}
          {authMode !== "reset" && <label>Email address<input type="email" autoComplete="email" value={authEmail} onChange={(event) => setAuthEmail(event.target.value)} required maxLength={254} /></label>}
          {!(["forgot"].includes(authMode)) && <label>Password<input type="password" autoComplete={authMode === "register" || authMode === "reset" ? "new-password" : "current-password"} value={authPassword} onChange={(event) => setAuthPassword(event.target.value)} required minLength={authMode === "register" || authMode === "reset" ? 10 : 1} maxLength={72} /></label>}
          {authMessage && <div className="ls-auth-message" role="status">{authMessage}</div>}
          {authError && <div className="ls-auth-error" role="alert">{authError}</div>}
          <button className="ls-primary-button ls-auth-submit" type="submit" disabled={authBusy}>
            {authBusy ? "Please wait..." : authMode === "register" ? "Create account" : authMode === "forgot" ? "Send reset link" : authMode === "reset" ? "Update password" : "Sign in"} <span>↗</span>
          </button>
          {authMode === "register" && authMessage && <button className="ls-auth-link" type="button" onClick={async () => { try { const result = await apiRequest("/api/auth/resend-verification", {method:"POST", headers:{"Content-Type":"application/json"}, body:JSON.stringify({email:authEmail.trim()})}); setAuthMessage(result.message); } catch (e) { setAuthError(e.message); } }}>Resend verification email</button>}
          {authMode === "login" && <button className="ls-auth-link" type="button" onClick={() => { setAuthMode("forgot"); setAuthError(""); setAuthMessage(""); }}>Forgot password?</button>}
          <div className="ls-auth-switch">
            {authMode === "register" ? "Already have an account?" : ["forgot", "reset"].includes(authMode) ? "Remember your password?" : "New to LearnSphere?"}
            <button type="button" onClick={() => { setAuthMode(authMode === "register" ? "login" : "login"); setAuthError(""); setAuthMessage(""); }}>
              {authMode === "register" ? "Sign in" : ["forgot", "reset"].includes(authMode) ? "Sign in" : "Create account"}
            </button>
          </div>
          {authMode === "register" && <small>Use at least 10 characters and no more than 72 UTF-8 bytes.</small>}
        </form>
      </div>
    );
  }

  if (authChecking) {
    return <div className="ls-auth-page"><div className="ls-loading"><span className="ls-spinner"></span> Verifying your sign-in...</div></div>;
  }

  const currentLesson = lessons[activeLesson];

  return (
    <div className="ls-app">
      <aside className={`ls-sidebar ${showMobileMenu ? "is-open" : ""}`}>
        <a className="ls-brand" href="#home">
          <span className="ls-brand-icon">L</span>
          <span>learn<span className="ls-brand-light">sphere</span></span>
        </a>

        <div className="ls-workspace">
          <span className="ls-workspace-avatar">LS</span>
          <div>
            <strong>My workspace</strong>
            <span>Personal learning</span>
          </div>
        </div>

        <div className="ls-nav-label">WORKSPACE</div>

        <nav className="ls-nav">
          <a className={`ls-nav-item ${courseView === "all" ? "active" : ""}`} href="#home" title="Overview" aria-label="Overview" onClick={() => { setCourseView("all"); setShowMobileMenu(false); }}>
            <span>⌂</span> Overview
          </a>
          <a className={`ls-nav-item ${courseView === "enrolled" ? "active" : ""}`} href="#courses" title="My courses" aria-label="My courses" onClick={() => { setCourseView("enrolled"); setActiveFilter("All courses"); setShowMobileMenu(false); }}>
            <span>▤</span> My courses
            <span className="ls-nav-count">{enrolledCourses.length}</span>
          </a>
          <a className="ls-nav-item" href="#progress" title="My progress" aria-label="My progress" onClick={() => { setCourseView("enrolled"); setShowMobileMenu(false); }}>
            <span>◷</span> My progress
          </a>
        </nav>

        <div className="ls-nav-label ls-nav-label-spaced">
          EXPLORE
        </div>

        <nav className="ls-nav">
          <a className="ls-nav-item" href="#courses" title="Discover courses" aria-label="Discover courses" onClick={() => { setCourseView("all"); setActiveFilter("All courses"); setShowMobileMenu(false); }}>
            <span>⌕</span> Discover courses
          </a>
          <button className="ls-nav-item ls-nav-action" type="button" onClick={() => { setShowLearningPaths(true); setShowMobileMenu(false); }}>
            <span>✧</span> Browse topics
          </button>
          {user?.role === "ADMIN" && <button className="ls-nav-item ls-nav-action" type="button" onClick={() => { setShowAdmin(true); setShowMobileMenu(false); loadAdminItems("courses"); }}><span>⚙</span> Admin management</button>}
        </nav>

        <div className="ls-sidebar-bottom">
          <div className="ls-sidebar-card">
            <div className="ls-sidebar-card-icon">✦</div>
            <strong>Keep growing.</strong>
            <p>Small steps today lead to big achievements tomorrow.</p>
            <a href="#courses" onClick={() => { setCourseView("enrolled"); setActiveFilter("All courses"); setShowMobileMenu(false); }}>Continue learning <span>↗</span></a>
          </div>

          <div className="ls-profile">
              <div className="ls-profile-avatar">{(user?.fullName || "S").slice(0, 1).toUpperCase()}</div>
            <div className="ls-profile-info">
              <strong>{user?.fullName || "Learner"}</strong>
              <button className="ls-logout" onClick={handleLogout}>Sign out</button>
            </div>
          </div>
        </div>
      </aside>

      {showMobileMenu && <button className="ls-mobile-backdrop" aria-label="Close navigation" onClick={() => setShowMobileMenu(false)} />}

      <main className="ls-main" id="home">
        <header className="ls-topbar">
          <button className="ls-mobile-menu" type="button" aria-label={showMobileMenu ? "Close navigation" : "Open navigation"} aria-expanded={showMobileMenu} onClick={() => setShowMobileMenu((open) => !open)}>
            {showMobileMenu ? "×" : "☰"}
          </button>
          <div className="ls-breadcrumb">
            Workspace <span>/</span> <strong>Overview</strong>
          </div>

          <div className="ls-topbar-right">
            <span className="ls-online">
              <span></span> Learning space
            </span>
            <button className="ls-icon-button" aria-label="Learning updates" aria-expanded={showNotifications} onClick={() => setShowNotifications((open) => !open)}>
              ♧
            </button>
            {showNotifications && (
              <section className="ls-notification-panel" aria-label="Learning updates">
                <strong>Learning updates</strong>
                {ongoingCourses.length ? (
                  <>
                    <p>You have {ongoingCourses.length} {ongoingCourses.length === 1 ? "course" : "courses"} in progress.</p>
                    <button type="button" onClick={() => { setCourseView("enrolled"); setShowNotifications(false); document.getElementById("courses")?.scrollIntoView({ behavior: "smooth" }); }}>Continue learning</button>
                  </>
                ) : enrolledCourses.length ? <p>You’ve completed all your enrolled courses. Great work.</p> : <p>Enroll in a course and your learning updates will appear here.</p>}
              </section>
            )}
            <div className="ls-topbar-avatar">{(user?.fullName || "S").slice(0, 1).toUpperCase()}</div>
          </div>
        </header>

        <div className="ls-content">
          {notice && (
            <div className={`ls-notice ${notice.type}`} role={notice.type === "error" ? "alert" : "status"}>
              <span>{notice.message}</span>
              <button type="button" aria-label="Dismiss message" onClick={() => setNotice(null)}>×</button>
            </div>
          )}
          <section className="ls-welcome">
            <div>
              <div className="ls-eyebrow">
                <span className="ls-eyebrow-dot"></span>
                YOUR PERSONAL LEARNING SPACE
              </div>
              <h1>
                Make today
                <br />
                <span>count.</span>
              </h1>
              <p>
                Every new skill starts with a single step.
                <br />
                Pick up where you left off and keep moving forward.
              </p>
              <a className="ls-primary-button" href="#courses" onClick={() => setCourseView("all")}>
                Explore your courses <span>↗</span>
              </a>
            </div>

            <div className="ls-hero-art" aria-hidden="true">
              <div className="ls-art-orbit ls-art-orbit-one"></div>
              <div className="ls-art-orbit ls-art-orbit-two"></div>
              <div className="ls-art-sun"></div>
              <div className="ls-art-book">
                <div className="ls-art-book-page">
                  <span></span>
                  <span></span>
                  <span></span>
                </div>
              </div>
              <div className="ls-art-sparkle">✦</div>
              <div className="ls-art-caption">
                <span>✧</span> A little progress every day
              </div>
            </div>
          </section>

          <section className="ls-stats" id="progress">
            <div className="ls-stat-card">
              <div className="ls-stat-icon violet">▤</div>
              <div>
                <span className="ls-stat-label">Available courses</span>
                <strong>{loading ? "—" : pageInfo.totalElements}</strong>
                <span className="ls-stat-foot">Ready to explore</span>
              </div>
            </div>

            <div className="ls-stat-card">
              <div className="ls-stat-icon blue">◷</div>
              <div>
                <span className="ls-stat-label">In progress</span>
                <strong>{loading ? "—" : enrolledCourses.length}</strong>
                <span className="ls-stat-foot">Keep up the momentum</span>
              </div>
            </div>

            <div className="ls-stat-card">
              <div className="ls-stat-icon green">✓</div>
              <div>
                <span className="ls-stat-label">Completed</span>
                <strong>{loading ? "—" : completedCourses.length}</strong>
                <span className="ls-stat-foot">Every milestone matters</span>
              </div>
            </div>

            <div className="ls-stat-card">
              <div className="ls-stat-icon orange">↗</div>
              <div>
                <span className="ls-stat-label">Learning progress</span>
                <strong>{loading ? "—" : `${averageProgress}%`}</strong>
                <span className="ls-stat-foot">Your journey so far</span>
              </div>
            </div>
          </section>

          <section className="ls-courses-section" id="courses">
            <div className="ls-section-heading">
              <div>
                <div className="ls-section-eyebrow">YOUR LEARNING JOURNEY</div>
                <h2>Explore your courses</h2>
                <p>Find your next skill and make it yours.</p>
              </div>

              <label className="ls-search">
                <span>⌕</span>
                <input
                  type="search"
                  ref={searchInputRef}
                  aria-label="Search courses"
                  placeholder="Search courses..."
                  value={search}
                  onChange={(event) => { setSearch(event.target.value); setPageInfo((current) => ({...current,page:0})); }}
                />
                <kbd>Ctrl K</kbd>
              </label>
            </div>

            <div className="ls-filters">
              {filters.map((filter) => (
                <button
                  key={filter}
                  className={`ls-filter ${
                    activeFilter === filter ? "selected" : ""
                  }`}
                  aria-pressed={activeFilter === filter}
                  onClick={() => { setActiveFilter(filter); setPageInfo((current) => ({...current,page:0})); }}
                >
                  {filter}
                </button>
              ))}
              <label className="ls-catalog-control">Level <select value={levelFilter} onChange={(event) => {setLevelFilter(event.target.value);setPageInfo((current)=>({...current,page:0}));}}><option value="">All levels</option><option>Beginner</option><option>Intermediate</option><option>Advanced</option></select></label>
              <label className="ls-catalog-control">Sort <select value={catalogSort} onChange={(event) => {setCatalogSort(event.target.value);setPageInfo((current)=>({...current,page:0}));}}><option value="name">Name</option><option value="category">Category</option><option value="level">Level</option><option value="duration">Duration</option><option value="priceMinor">Price</option></select></label>
            </div>

            {error && (
              <div className="ls-error">
                <strong>Unable to load courses</strong>
                <p>{error}</p>
                <button onClick={() => window.location.reload()}>
                  Try again
                </button>
              </div>
            )}

            {loading ? (
              <div className="ls-loading">
                <span className="ls-spinner"></span>
                Loading your courses...
              </div>
            ) : (
              <div className="ls-course-grid">
                {filteredCourses.map((course) => (
                  <article className="ls-course-card" key={course.id}>
                    <div className={`ls-course-cover ${course.color}`}>
                      {course.thumbnailUrl && <img className="ls-course-thumbnail" src={course.thumbnailUrl} alt="" loading="lazy" />}
                      <span className="ls-course-category">
                        {course.category}
                      </span>
                      <div className="ls-course-art">
                        <span>{course.icon}</span>
                      </div>
                      <span className="ls-course-cover-sparkle">✦</span>
                    </div>

                    <div className="ls-course-body">
                      <div className="ls-course-meta">
                        <span>{course.level}</span>
                        <span>·</span>
                        <span>{course.duration}</span>
                      </div>

                      <h3>{course.name}</h3>
                      <p>{course.description}</p>

                      <div className="ls-course-progress">
                        <div className="ls-progress-label">
                          <span>
                            {course.progress > 0
                              ? "Your progress"
                              : "Ready to begin"}
                          </span>
                          <strong>{course.progress}%</strong>
                        </div>
                        <div className="ls-progress-track">
                          <div
                            className="ls-progress-fill"
                            role="progressbar"
                            aria-valuemin="0"
                            aria-valuemax="100"
                            aria-valuenow={course.progress}
                            aria-label={`${course.name} progress`}
                            style={{ width: `${course.progress}%` }}
                          ></div>
                        </div>
                      </div>

                      <button
                        className="ls-course-button"
                        onClick={() => handleCourseAction(course)}
                        disabled={openingCourseId !== null}
                      >
                        {openingCourseId === course.id ? "Opening..." : course.enrolled
                          ? "Continue learning"
                          : Number(course.priceMinor) > 0 ? `Buy course · ${(course.priceMinor / 100).toLocaleString(undefined, { style: "currency", currency: course.currency || "INR" })}` : "Start learning"}
                        <span>↗</span>
                      </button>
                    </div>
                  </article>
                ))}
              </div>
            )}

            {!loading && pageInfo.totalPages > 1 && <nav className="ls-pagination" aria-label="Course pages">
              <button type="button" disabled={pageInfo.page <= 0} onClick={() => setPageInfo((current) => ({ ...current, page: current.page - 1 }))}>Previous</button>
              <span>Page {pageInfo.page + 1} of {pageInfo.totalPages}</span>
              <button type="button" disabled={pageInfo.page + 1 >= pageInfo.totalPages} onClick={() => setPageInfo((current) => ({ ...current, page: current.page + 1 }))}>Next</button>
            </nav>}

            {!loading && !error && filteredCourses.length === 0 && (
              <div className="ls-empty">
                <span>⌕</span>
                <h3>{courseView === "enrolled" && enrolledCourses.length === 0 ? "No courses in your learning list yet" : allCourses.length === 0 ? "No courses are available right now" : "No courses found"}</h3>
                <p>{courseView === "enrolled" && enrolledCourses.length === 0 ? "Explore the catalog and choose a course to get started." : allCourses.length === 0 ? "Please check back later." : "Try another search or category."}</p>
                {courseView === "enrolled" && enrolledCourses.length === 0 ? (
                  <button onClick={() => { setCourseView("all"); setActiveFilter("All courses"); }}>Discover courses</button>
                ) : allCourses.length > 0 && (
                  <button onClick={() => { setSearch(""); setActiveFilter("All courses"); }}>Clear filters</button>
                )}
              </div>
            )}
          </section>

          {activeCourse && (
            <div className="ls-learning-backdrop" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) setActiveCourse(null); }}>
              <section className="ls-learning-panel" role="dialog" aria-modal="true" aria-labelledby="ls-learning-title">
                <header className="ls-learning-header">
                  <div><span className="ls-section-eyebrow">COURSE PLAYER</span><h2 id="ls-learning-title">{activeCourse.name}</h2></div>
                  <button className="ls-learning-close" aria-label="Close course player" onClick={() => setActiveCourse(null)}>×</button>
                </header>
                {currentLesson ? <div className="ls-learning-layout">
                  <nav className="ls-lesson-list" aria-label="Course lessons">
                    {lessons.map((lesson, index) => (
                      <button key={lesson.id} className={`ls-lesson-item ${activeLesson === index ? "selected" : ""}`} onClick={() => setActiveLesson(index)}>
                        <span className={`ls-lesson-check ${lesson.completed ? "complete" : ""}`}>{lesson.completed ? "✓" : index + 1}</span>
                        <span>{lesson.title}</span>
                      </button>
                    ))}
                  </nav>
                  <article className="ls-lesson-content">
                    <div className="ls-section-eyebrow">LESSON {activeLesson + 1} OF {lessons.length}</div>
                    <h3>{currentLesson.title}</h3>
                    <p className="ls-lesson-summary">{currentLesson.description}</p>
                    {currentLesson.videoUrl && <video className="ls-lesson-video" controls preload="metadata" src={currentLesson.videoUrl}>Your browser does not support video playback.</video>}
                    <div className="ls-lesson-reading">{currentLesson.content.replace(/\\n/g, "\n").split("\n").map((paragraph, index) => paragraph ? <p key={index}>{paragraph}</p> : null)}</div>
                    <div className="ls-lesson-footer">
                      <span>{activeCourse.progress}% course progress</span>
                      <button className="ls-primary-button" disabled={lessonBusy || currentLesson.completed} onClick={completeActiveLesson}>
                        {lessonBusy ? "Saving..." : currentLesson.completed ? "Lesson completed" : "Mark lesson complete"} <span>✓</span>
                      </button>
                    </div>
                  </article>
                </div> : <div className="ls-course-empty"><h3>No lessons are available yet</h3><p>This course is enrolled, but its lessons have not been added.</p></div>}
              </section>
            </div>
          )}

          {showLearningPaths && (
            <div className="ls-learning-backdrop" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) setShowLearningPaths(false); }}>
              <section className="ls-paths-panel" role="dialog" aria-modal="true" aria-labelledby="ls-paths-title">
                <header className="ls-learning-header">
                  <div><span className="ls-section-eyebrow">COURSE TOPICS</span><h2 id="ls-paths-title">Browse by topic</h2></div>
                  <button className="ls-learning-close" aria-label="Close learning paths" onClick={() => setShowLearningPaths(false)}>×</button>
                </header>
                <p>Choose a topic to filter the live course catalog.</p>
                <div className="ls-path-options">
                  {filters.slice(1).map((category) => {
                    const count = allCourses.filter((course) => course.category === category).length;
                    return <button key={category} type="button" disabled={!count} onClick={() => { setCourseView("all"); setActiveFilter(category); setShowLearningPaths(false); document.getElementById("courses")?.scrollIntoView({ behavior: "smooth" }); }}>
                      <strong>{category}</strong><span>{count} {count === 1 ? "course" : "courses"}</span><span aria-hidden="true">↗</span>
                    </button>;
                  })}
                </div>
              </section>
            </div>
          )}

          {showAdmin && user?.role === "ADMIN" && <div className="ls-learning-backdrop" role="presentation" onMouseDown={(event)=>{if(event.target===event.currentTarget)setShowAdmin(false);}}>
            <section className="ls-admin-panel" role="dialog" aria-modal="true" aria-labelledby="ls-admin-title">
              <header className="ls-learning-header"><div><span className="ls-section-eyebrow">ADMINISTRATION</span><h2 id="ls-admin-title">Manage LearnSphere</h2></div><button className="ls-learning-close" aria-label="Close admin management" onClick={()=>setShowAdmin(false)}>×</button></header>
              <div className="ls-admin-tabs">{["courses","users","enrollments"].map(tab=><button key={tab} type="button" aria-pressed={adminTab===tab} onClick={()=>{setAdminTab(tab);setAdminCourseId(null);loadAdminItems(tab);}}>{tab}</button>)}</div>
              {adminError&&<div className="ls-auth-error" role="alert">{adminError}</div>}
              {adminBusy?<div className="ls-loading"><span className="ls-spinner"/> Loading management data…</div>:<div className="ls-admin-list">
                {adminTab==="courses"&&adminItems.map(item=><article className="ls-admin-row" key={item.id}><div><strong>{item.name}</strong><small>{item.category} · {item.level} · {item.published?"Published":"Unpublished"} · {item.priceMinor ? `${item.currency} ${(item.priceMinor/100).toFixed(2)}` : "Free"}</small></div><div><button type="button" onClick={()=>{setCourseDraft({name:item.name,category:item.category,level:item.level,duration:item.duration,description:item.description,icon:item.icon,color:item.color,priceMinor:item.priceMinor,currency:item.currency,thumbnailUrl:item.thumbnailUrl||""});setEditingCourseId(item.id);setAdminCourseId(null);}}>Edit</button><button type="button" onClick={()=>openAdminLessons(item)}>Lessons</button><button type="button" onClick={async()=>{try{await apiRequest(`/api/admin/courses/${item.id}/${item.published?"":"publish"}`,{method:item.published?"DELETE":"POST"});await loadAdminItems("courses");}catch(e){setAdminError(e.message);}}}>{item.published?"Unpublish":"Publish"}</button></div></article>)}
                {adminTab==="users"&&adminItems.map(item=><article className="ls-admin-row" key={item.id}><div><strong>{item.fullName}</strong><small>{item.email} · {item.role} · {item.emailVerified?"verified":"unverified"}</small></div><div><button type="button" onClick={async()=>{try{await apiRequest(`/api/admin/users/${item.id}`,{method:"PUT",headers:{"Content-Type":"application/json"},body:JSON.stringify({fullName:item.fullName,email:item.email,role:item.role==="ADMIN"?"LEARNER":"ADMIN",active:true})});await loadAdminItems("users");}catch(e){setAdminError(e.message);}}}>{item.role==="ADMIN"?"Make learner":"Make admin"}</button><button type="button" onClick={()=>deleteAdminItem(item)}>Deactivate</button></div></article>)}
                {adminTab==="enrollments"&&adminItems.map(item=><article className="ls-admin-row" key={item.id}><div><strong>{item.courseName}</strong><small>{item.email} · {item.progress}% complete</small></div><button type="button" onClick={()=>deleteAdminItem(item)}>Remove enrollment</button></article>)}
                {!adminItems.length&&<p>No {adminTab} found.</p>}
              </div>}
              {adminTab==="courses"&&!adminCourseId&&<form className="ls-admin-form" onSubmit={saveAdminCourse}><h3>{editingCourseId?"Edit course":"Create course"}</h3><input aria-label="Course name" placeholder="Course name" required maxLength={180} value={courseDraft.name} onChange={e=>setCourseDraft({...courseDraft,name:e.target.value})}/><div><input aria-label="Category" placeholder="Category" required value={courseDraft.category} onChange={e=>setCourseDraft({...courseDraft,category:e.target.value})}/><select aria-label="Difficulty" value={courseDraft.level} onChange={e=>setCourseDraft({...courseDraft,level:e.target.value})}><option>Beginner</option><option>Intermediate</option><option>Advanced</option></select></div><input aria-label="Duration" placeholder="Duration" required value={courseDraft.duration} onChange={e=>setCourseDraft({...courseDraft,duration:e.target.value})}/><textarea aria-label="Course description" placeholder="Course description" required maxLength={1200} value={courseDraft.description} onChange={e=>setCourseDraft({...courseDraft,description:e.target.value})}/><div><input aria-label="Price in minor currency units" type="number" min="0" placeholder="Price (paise/cents)" value={courseDraft.priceMinor} onChange={e=>setCourseDraft({...courseDraft,priceMinor:e.target.value})}/><input aria-label="Currency" maxLength={3} value={courseDraft.currency} onChange={e=>setCourseDraft({...courseDraft,currency:e.target.value.toUpperCase()})}/></div><input aria-label="HTTPS course thumbnail URL" type="url" placeholder="HTTPS thumbnail URL (optional)" value={courseDraft.thumbnailUrl} onChange={e=>setCourseDraft({...courseDraft,thumbnailUrl:e.target.value})}/><button className="ls-primary-button" disabled={adminBusy}>{editingCourseId?"Save changes":"Create course"}</button>{editingCourseId&&<button type="button" onClick={()=>{setEditingCourseId(null);setCourseDraft({name:"",category:"Programming",level:"Beginner",duration:"4 weeks",description:"",icon:"",color:"violet",priceMinor:0,currency:"INR",thumbnailUrl:""});}}>Cancel edit</button>}</form>}
              {adminTab==="courses"&&adminCourseId&&<div className="ls-admin-lesson-area"><h3>Course lessons</h3>{adminLessons.map(lesson=><article className="ls-admin-row" key={lesson.id}><div><strong>{lesson.sortOrder}. {lesson.title}</strong><small>{lesson.videoUrl||"Text lesson"}</small></div><div><button type="button" onClick={()=>{setEditingLessonId(lesson.id);setLessonDraft({title:lesson.title,description:lesson.description,content:lesson.content,sortOrder:lesson.sortOrder,videoUrl:lesson.videoUrl||""});}}>Edit</button><button type="button" onClick={async()=>{await apiRequest(`/api/admin/courses/${adminCourseId}/lessons/${lesson.id}`,{method:"DELETE"});setAdminLessons(await apiRequest(`/api/admin/courses/${adminCourseId}/lessons`));}}>Remove</button></div></article>)}<form className="ls-admin-form" onSubmit={saveAdminLesson}><h3>{editingLessonId?"Edit lesson":"Add lesson"}</h3><input aria-label="Lesson title" placeholder="Lesson title" required maxLength={180} value={lessonDraft.title} onChange={e=>setLessonDraft({...lessonDraft,title:e.target.value})}/><input aria-label="Lesson summary" placeholder="Lesson summary" required maxLength={500} value={lessonDraft.description} onChange={e=>setLessonDraft({...lessonDraft,description:e.target.value})}/><textarea aria-label="Lesson content" placeholder="Lesson content" required value={lessonDraft.content} onChange={e=>setLessonDraft({...lessonDraft,content:e.target.value})}/><input aria-label="HTTPS video URL" type="url" placeholder="HTTPS video URL (optional)" value={lessonDraft.videoUrl} onChange={e=>setLessonDraft({...lessonDraft,videoUrl:e.target.value})}/><button className="ls-primary-button">{editingLessonId?"Save lesson":"Add lesson"}</button><button type="button" onClick={()=>{setAdminCourseId(null);setEditingLessonId(null);}}>Back to courses</button></form></div>}
            </section>
          </div>}

          <footer className="ls-footer">
            <span>© 2026 LearnSphere</span>
            <span>Designed for your next big thing. <b>✦</b></span>
          </footer>
        </div>
      </main>
    </div>
  );
}

export default App;
