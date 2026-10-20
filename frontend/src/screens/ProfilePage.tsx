import { useEffect, useLayoutEffect, useRef, useState } from "react";
import { api, tokenStore } from "../api";
import type { User } from "../api";
import { useAuth } from "../context/session";
import FormField from "../components/FormField";
import { PageTitle, ErrorNotice, errorMessage } from "../components/BankingUi";
import LoadingState from "../components/LoadingState";
export default function ProfilePage() {
  const { user, logout, updateUser } = useAuth();
  const session = useRef({ mounted: false, userId: user?.id });
  useLayoutEffect(() => {
    session.current.userId = user?.id;
  }, [user?.id]);
  useLayoutEffect(() => {
    const state = session.current;
    state.mounted = true;
    return () => {
      state.mounted = false;
    };
  }, []);
  const [profile, setProfile] = useState<User | null>(null);
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [saved, setSaved] = useState(false);
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    let active = true;
    api
      .profile()
      .then((value) => {
        if (active) {
          setProfile(value);
          setName(value.fullName);
          setEmail(value.email ?? "");
          setError("");
        }
      })
      .catch((e) => {
        if (active) setError(errorMessage(e));
      });
    return () => {
      active = false;
    };
  }, [attempt]);
  async function save() {
    if (busy) return;
    if (!name.trim()) {
      setError("Enter your full name.");
      return;
    }
    const token = tokenStore.get();
    const userId = user?.id;
    const current = () =>
      session.current.mounted &&
      !!token &&
      tokenStore.get() === token &&
      session.current.userId === userId;
    setBusy(true);
    setError("");
    setSaved(false);
    try {
      const value = await api.updateProfile(name.trim(), email.trim() || null);
      if (!current() || value.id !== userId) return;
      setProfile(value);
      updateUser(value);
      setSaved(true);
    } catch (e) {
      if (current()) setError(errorMessage(e));
    } finally {
      if (current()) setBusy(false);
    }
  }
  return (
    <>
      <PageTitle eyebrow="YOUR DETAILS, AT A GLANCE" title="Your profile">
        A familiar place for your account and security.
      </PageTitle>
      <div className="payment-grid">
        <section className="bank-panel">
          <h2>Personal details</h2>
          <ErrorNotice error={error} />
          {profile ? (
            <form
              onSubmit={(e) => {
                e.preventDefault();
                void save();
              }}
            >
              <fieldset disabled={busy}>
                <FormField
                  id="profile-name"
                  label="Full name"
                  value={name}
                  onChange={(value) => {
                    setName(value);
                    setSaved(false);
                  }}
                  maxLength={100}
                  required
                />
                <FormField
                  id="profile-email"
                  label="Email (optional)"
                  type="email"
                  value={email}
                  onChange={(value) => {
                    setEmail(value);
                    setSaved(false);
                  }}
                  maxLength={254}
                />
                <FormField
                  id="profile-phone"
                  label="Verified mobile number"
                  value={profile.mobileNumber}
                  onChange={() => {}}
                  readOnly
                  hint="Your verified phone is linked to your account."
                />
                <dl className="detail-list">
                  <div>
                    <dt>Account identifier</dt>
                    <dd>{profile.accountIdentifier}</dd>
                  </div>
                  <div>
                    <dt>Member since</dt>
                    <dd>
                      {profile.createdAt
                        ? new Date(profile.createdAt).toLocaleDateString(
                            "en-PH",
                            { dateStyle: "long" },
                          )
                        : "Not available"}
                    </dd>
                  </div>
                </dl>
                <button className="btn">
                  {busy ? "Saving…" : "Save profile"}
                </button>
              </fieldset>
              {saved && (
                <p className="success" role="status">
                  Profile saved.
                </p>
              )}
            </form>
          ) : error ? (
            <button
              className="secondary"
              onClick={() => setAttempt(attempt + 1)}
            >
              Retry profile
            </button>
          ) : (
            <LoadingState label="Loading your details…" />
          )}
        </section>
        <section className="bank-panel soft-panel">
          <span className="eyebrow">PEACE OF MIND</span>
          <h2>Account security</h2>
          <p>
            Recover your PIN using a verification code sent to your registered
            mobile.
          </p>
          <a className="secondary" href="#/forgot-pin">
            Recover PIN by SMS
          </a>
          <hr />
          <p>Finished for now? End your session on this device.</p>
          <button className="secondary" onClick={logout}>
            Log out
          </button>
        </section>
      </div>
    </>
  );
}
