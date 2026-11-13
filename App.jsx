
import { useEffect, useMemo, useState } from "react";
import "./App.css";

const API_BASE_URL = import.meta.env.VITE_API_URL || "http://localhost:8080";
const API_URL = `${API_BASE_URL}/api/courses`;

const courseDetails = [
  {
    category: "Programming",
    level: "Beginner",
    duration: "6 weeks",
    description: "Build a strong foundation in Java and object-oriented programming.",
    icon: "J",
    color: "violet",
    progress: 68,
  },
  {
    category: "Programming",
    level: "Beginner",
    duration: "5 weeks",
    description: "Learn Python fundamentals and start building useful applications.",
    icon: "Py",
    color: "blue",
    progress: 42,
  },
  {
    category: "Development",
    level: "Intermediate",
    duration: "8 weeks",
    description: "Create beautiful, responsive websites using modern web technologies.",
    icon: "W",
    color: "orange",
    progress: 25,
  },
  {
    category: "Computer Science",
    level: "Intermediate",
    duration: "7 weeks",
    description: "Understand essential data structures and efficient algorithms.",
    icon: "DS",
    color: "green",
    progress: 0,
  },
  {
    category: "Computer Science",
    level: "Intermediate",
    duration: "6 weeks",
    description: "Explore databases, SQL queries, and relational database design.",
    icon: "DB",
    color: "pink",
    progress: 0,
  },
];

const filters = ["All courses", "Programming", "Development", "Computer Science"];

function App() {
  const [courses, setCourses] = useState([]);
  const [token, setToken] = useState(() => localStorage.getItem("learnsphere-token") || "");
  const [user, setUser] = useState(() => {
    try { return JSON.parse(localStorage.getItem("learnsphere-user") || "null"); }
    catch { return null; }
  });
  const [authMode, setAuthMode] = useState("login");
  const [authName, setAuthName] = useState("");
  const [authEmail, setAuthEmail] = useState("");
  const [authPassword, setAuthPassword] = useState("");
  const [authError, setAuthError] = useState("");
  const [authBusy, setAuthBusy] = useState(false);
  const [activeCourse, setActiveCourse] = useState(null);
  const [lessons, setLessons] = useState([]);
  const [activeLesson, setActiveLesson] = useState(0);
  const [lessonBusy, setLessonBusy] = useState(false);
  const [search, setSearch] = useState("");
  const [activeFilter, setActiveFilter] = useState("All courses");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    if (!token) {
      setLoading(false);
      setCourses([]);
      return undefined;
    }
    let cancelled = false;

    async function loadCourses() {
      try {
        setLoading(true);
        setError("");

        const response = await fetch(API_URL, {
          headers: { Authorization: `Bearer ${token}` },
        });

        if (!response.ok) {
          throw new Error("Unable to load courses.");
        }

        const data = await response.json();

        if (!cancelled) {
          setCourses(Array.isArray(data) ? data : []);
        }
      } catch {
        if (!cancelled) {
          setError(
            "We couldn't connect to your learning server. Make sure Spring Boot is running."
          );
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    }

    loadCourses();

    return () => {
      cancelled = true;
    };
  }, [token]);

  async function handleAuthSubmit(event) {
    event.preventDefault();
    setAuthBusy(true);
    setAuthError("");
    try {
      const endpoint = authMode === "register" ? "register" : "login";
      const payload = authMode === "register"
        ? { fullName: authName, email: authEmail, password: authPassword }
        : { email: authEmail, password: authPassword };
      const response = await fetch(`${API_BASE_URL}/api/auth/${endpoint}`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload),
      });
      const data = await response.json();
      if (!response.ok) throw new Error(data.message || "Unable to sign in. Please try again.");
      localStorage.setItem("learnsphere-token", data.accessToken);
      localStorage.setItem("learnsphere-user", JSON.stringify(data.user));
      setToken(data.accessToken);
      setUser(data.user);
      setAuthPassword("");
    } catch (requestError) {
      setAuthError(requestError.message || "The learning server is unavailable.");
    } finally {
      setAuthBusy(false);
    }
  }

  function handleLogout() {
    localStorage.removeItem("learnsphere-token");
    localStorage.removeItem("learnsphere-user");
    setToken("");
    setUser(null);
  }

  async function handleCourseAction(course) {
    try {
      let enrolledCourse = course;
      if (!course.enrolled) {
        const enrollResponse = await fetch(`${API_URL}/${course.id}/enroll`, {
          method: "POST",
          headers: { Authorization: `Bearer ${token}` },
        });
        const enrolledData = await enrollResponse.json();
        if (!enrollResponse.ok) throw new Error(enrolledData.message || "Unable to enroll in this course.");
        enrolledCourse = enrolledData;
        setCourses((current) => current.map((item) => item.id === enrolledData.id ? enrolledData : item));
      }
      const response = await fetch(`${API_URL}/${course.id}/lessons`, {
        headers: { Authorization: `Bearer ${token}` },
      });
      const data = await response.json();
      if (!response.ok) throw new Error(data.message || "Unable to load course lessons.");
      setCourses((current) => current.map((item) => item.id === enrolledCourse.id ? enrolledCourse : item));
      setActiveCourse(enrolledCourse);
      setLessons(data);
      const firstIncomplete = data.findIndex((lesson) => !lesson.completed);
      setActiveLesson(firstIncomplete < 0 ? 0 : firstIncomplete);
    } catch (requestError) {
      setError(requestError.message || "Unable to enroll in this course.");
    }
  }

  async function completeActiveLesson() {
    const lesson = lessons[activeLesson];
    if (!lesson || lesson.completed || lessonBusy) return;
    setLessonBusy(true);
    try {
      const response = await fetch(`${API_URL}/${activeCourse.id}/lessons/${lesson.id}/complete`, {
        method: "POST",
        headers: { Authorization: `Bearer ${token}` },
      });
      const updated = await response.json();
      if (!response.ok) throw new Error(updated.message || "Unable to save lesson progress.");
      setLessons((current) => current.map((item) => item.id === updated.id ? updated : item));
      setCourses((current) => current.map((item) => item.id === activeCourse.id
        ? { ...item, progress: updated.courseProgress, enrolled: true }
        : item));
      setActiveCourse((current) => ({ ...current, progress: updated.courseProgress, enrolled: true }));
    } catch (requestError) {
      setError(requestError.message || "Unable to save lesson progress.");
    } finally {
      setLessonBusy(false);
    }
  }

  const allCourses = useMemo(() => {
    return courses.map((course, index) => {
      const name = typeof course === "string"
        ? course
        : course.name || course.title || "Untitled course";

      const details = courseDetails.find(
        (item) => item.icon === name
      );

      const fallback = courseDetails[index % courseDetails.length];

      return {
        id: course.id ?? index,
        name,
        category: course.category || details?.category || fallback.category,
        level: course.level || details?.level || fallback.level,
        duration: course.duration || details?.duration || fallback.duration,
        description:
          course.description || details?.description || fallback.description,
        icon: course.icon || fallback.icon,
        color: course.color || fallback.color,
        progress: course.progress ?? fallback.progress,
        enrolled: course.enrolled ?? (course.progress > 0),
      };
    });
  }, [courses]);

  const filteredCourses = allCourses.filter((course) => {
    const matchesSearch = course.name
      .toLowerCase()
      .includes(search.toLowerCase());

    const matchesCategory =
      activeFilter === "All courses" ||
      course.category === activeFilter;

    return matchesSearch && matchesCategory;
  });

  const enrolledCourses = allCourses.filter(
    (course) => course.enrolled
  );

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

  if (!token) {
    return (
      <div className="ls-auth-page">
        <a className="ls-brand ls-auth-brand" href="#home">
          <span className="ls-brand-icon">L</span>
          <span>learn<span className="ls-brand-light">sphere</span></span>
        </a>
        <form className="ls-auth-card" onSubmit={handleAuthSubmit}>
          <div className="ls-eyebrow"><span className="ls-eyebrow-dot"></span> YOUR PERSONAL LEARNING SPACE</div>
          <h1>{authMode === "register" ? "Create your account." : "Welcome back."}</h1>
          <p>{authMode === "register" ? "Start building skills, one course at a time." : "Sign in to continue your learning journey."}</p>
          {authMode === "register" && (
            <label>Full name<input autoComplete="name" value={authName} onChange={(event) => setAuthName(event.target.value)} required maxLength={100} /></label>
          )}
          <label>Email address<input type="email" autoComplete="email" value={authEmail} onChange={(event) => setAuthEmail(event.target.value)} required maxLength={254} /></label>
          <label>Password<input type="password" autoComplete={authMode === "register" ? "new-password" : "current-password"} value={authPassword} onChange={(event) => setAuthPassword(event.target.value)} required minLength={authMode === "register" ? 10 : 1} maxLength={72} /></label>
          {authError && <div className="ls-auth-error" role="alert">{authError}</div>}
          <button className="ls-primary-button ls-auth-submit" type="submit" disabled={authBusy}>
            {authBusy ? "Please wait..." : authMode === "register" ? "Create account" : "Sign in"} <span>↗</span>
          </button>
          <div className="ls-auth-switch">
            {authMode === "register" ? "Already have an account?" : "New to LearnSphere?"}
            <button type="button" onClick={() => { setAuthMode(authMode === "register" ? "login" : "register"); setAuthError(""); }}>
              {authMode === "register" ? "Sign in" : "Create account"}
            </button>
          </div>
          {authMode === "register" && <small>Use at least 10 characters for your password.</small>}
        </form>
      </div>
    );
  }

  const currentLesson = lessons[activeLesson];

  return (
    <div className="ls-app">
      <aside className="ls-sidebar">
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
          <span className="ls-chevron">⌄</span>
        </div>

        <div className="ls-nav-label">WORKSPACE</div>

        <nav className="ls-nav">
          <a className="ls-nav-item active" href="#home">
            <span>⌂</span> Overview
          </a>
          <a className="ls-nav-item" href="#courses">
            <span>▤</span> My courses
            <span className="ls-nav-count">{allCourses.length}</span>
          </a>
          <a className="ls-nav-item" href="#progress">
            <span>◷</span> My progress
          </a>
        </nav>

        <div className="ls-nav-label ls-nav-label-spaced">
          EXPLORE
        </div>

        <nav className="ls-nav">
          <a className="ls-nav-item" href="#courses">
            <span>⌕</span> Discover courses
          </a>
          <a className="ls-nav-item" href="#courses">
            <span>✧</span> Learning paths
          </a>
        </nav>

        <div className="ls-sidebar-bottom">
          <div className="ls-sidebar-card">
            <div className="ls-sidebar-card-icon">✦</div>
            <strong>Keep growing.</strong>
            <p>Small steps today lead to big achievements tomorrow.</p>
            <a href="#courses">Continue learning <span>↗</span></a>
          </div>

          <div className="ls-profile">
              <div className="ls-profile-avatar">{(user?.fullName || "S").slice(0, 1).toUpperCase()}</div>
            <div className="ls-profile-info">
              <strong>{user?.fullName || "Learner"}</strong>
              <button className="ls-logout" onClick={handleLogout}>Sign out</button>
            </div>
            <span className="ls-profile-menu">···</span>
          </div>
        </div>
      </aside>

      <main className="ls-main" id="home">
        <header className="ls-topbar">
          <div className="ls-breadcrumb">
            Workspace <span>/</span> <strong>Overview</strong>
          </div>

          <div className="ls-topbar-right">
            <span className="ls-online">
              <span></span> Learning space
            </span>
            <button className="ls-icon-button" aria-label="Notifications">
              ♧
            </button>
            <div className="ls-topbar-avatar">{(user?.fullName || "S").slice(0, 1).toUpperCase()}</div>
          </div>
        </header>

        <div className="ls-content">
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
              <a className="ls-primary-button" href="#courses">
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
                <strong>{loading ? "—" : allCourses.length}</strong>
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
                  placeholder="Search courses..."
                  value={search}
                  onChange={(event) => setSearch(event.target.value)}
                />
                <kbd>⌘ K</kbd>
              </label>
            </div>

            <div className="ls-filters">
              {filters.map((filter) => (
                <button
                  key={filter}
                  className={`ls-filter ${
                    activeFilter === filter ? "selected" : ""
                  }`}
                  onClick={() => setActiveFilter(filter)}
                >
                  {filter}
                </button>
              ))}
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
                            style={{ width: `${course.progress}%` }}
                          ></div>
                        </div>
                      </div>

                      <button
                        className="ls-course-button"
                        onClick={() => handleCourseAction(course)}
                      >
                        {course.enrolled
                          ? "Continue learning"
                          : "Start learning"}
                        <span>↗</span>
                      </button>
                    </div>
                  </article>
                ))}
              </div>
            )}

            {!loading && !error && filteredCourses.length === 0 && (
              <div className="ls-empty">
                <span>⌕</span>
                <h3>No courses found</h3>
                <p>Try another search or category.</p>
                <button
                  onClick={() => {
                    setSearch("");
                    setActiveFilter("All courses");
                  }}
                >
                  Clear filters
                </button>
              </div>
            )}
          </section>

          {activeCourse && currentLesson && (
            <div className="ls-learning-backdrop" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) setActiveCourse(null); }}>
              <section className="ls-learning-panel" role="dialog" aria-modal="true" aria-labelledby="ls-learning-title">
                <header className="ls-learning-header">
                  <div><span className="ls-section-eyebrow">COURSE PLAYER</span><h2 id="ls-learning-title">{activeCourse.name}</h2></div>
                  <button className="ls-learning-close" aria-label="Close course player" onClick={() => setActiveCourse(null)}>×</button>
                </header>
                <div className="ls-learning-layout">
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
                    <div className="ls-lesson-reading">{currentLesson.content.replace(/\\n/g, "\n").split("\n").map((paragraph, index) => paragraph ? <p key={index}>{paragraph}</p> : null)}</div>
                    <div className="ls-lesson-footer">
                      <span>{activeCourse.progress}% course progress</span>
                      <button className="ls-primary-button" disabled={lessonBusy || currentLesson.completed} onClick={completeActiveLesson}>
                        {lessonBusy ? "Saving..." : currentLesson.completed ? "Lesson completed" : "Mark lesson complete"} <span>✓</span>
                      </button>
                    </div>
                  </article>
                </div>
              </section>
            </div>
          )}

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
