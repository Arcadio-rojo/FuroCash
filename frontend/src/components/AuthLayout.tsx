import type { ReactNode } from "react";
export default function AuthLayout({
  eyebrow,
  title,
  description,
  children,
}: {
  eyebrow: string;
  title: string;
  description: string;
  children: ReactNode;
}) {
  return (
    <div className="auth-shell">
      <aside className="auth-story">
        <a className="brand" href="#/login">
          <span className="brand-mark">f.</span> FuroCash
          <span className="badge">DEMO</span>
        </a>
        <div className="story-content">
          <span className="eyebrow light">YOUR EVERYDAY MONEY COMPANION</span>
          <h1>
            A little more
            <br />
            freedom.
            <br />
            <em>Every day.</em>
          </h1>
          <p>
            Send, save, and stay on top of your money.
            <br />
            All in one simple place.
          </p>
          <div className="story-card">
            <span>Made for your next move</span>
            <strong>
              Less hassle.
              <br />
              More possibility.
            </strong>
            <div className="story-line" />
          </div>
        </div>
        <p className="story-footer">FuroCash · Your money, your pace.</p>
      </aside>
      <main className="auth-main">
        <a className="brand mobile-brand" href="#/login">
          <span className="brand-mark">f.</span> FuroCash
        </a>
        <div className="auth-form">
          <span className="eyebrow">{eyebrow}</span>
          <h2>{title}</h2>
          <p className="lede">{description}</p>
          {children}
        </div>
        <p className="auth-footnote">
          A local banking demo. Use test details and demo funds.
        </p>
      </main>
    </div>
  );
}
