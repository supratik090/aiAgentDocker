import React, { FormEvent, useEffect, useMemo, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { Bot, Github, GitBranch, Loader2, SendHorizonal, UserRound } from 'lucide-react';
import './styles.css';

type Message = {
  id: string;
  role: 'assistant' | 'user';
  text: string;
};

type ConversationResponse = {
  id: string;
  gitUrl: string;
  status: string;
  assistantMessage: string;
};

type AuthenticatedUser = {
  authenticated: boolean;
  login: string | null;
  name: string | null;
  avatarUrl: string | null;
};

type CloneRepositoryResponse = {
  repositoryWorkspaceId: string;
  localPath: string;
  status: string;
};

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:9040';

const initialMessages: Message[] = [
  {
    id: 'welcome',
    role: 'assistant',
    text: 'Hi, I am your AI Docker Agent. Login with GitHub, then share an HTTPS Git URL for a Java Spring Maven project that I can read.'
  }
];

function App() {
  const [messages, setMessages] = useState<Message[]>(initialMessages);
  const [gitUrl, setGitUrl] = useState('');
  const [branch, setBranch] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [conversationId, setConversationId] = useState<string | null>(null);
  const [user, setUser] = useState<AuthenticatedUser | null>(null);
  const canSubmit = useMemo(() => gitUrl.trim().length > 0 && !isSubmitting, [gitUrl, isSubmitting]);

  useEffect(() => {
    fetch(`${apiBaseUrl}/api/auth/me`, { credentials: 'include' })
      .then((response) => response.json())
      .then((currentUser: AuthenticatedUser) => setUser(currentUser))
      .catch(() => setUser({ authenticated: false, login: null, name: null, avatarUrl: null }));
  }, []);

  async function submitGitUrl(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!canSubmit) {
      return;
    }

    if (!user?.authenticated) {
      setMessages((current) => [
        ...current,
        {
          id: crypto.randomUUID(),
          role: 'assistant',
          text: 'Please login with GitHub first so I can save this conversation under your account and clone repositories with your read access.'
        }
      ]);
      return;
    }

    const trimmedGitUrl = gitUrl.trim();
    setMessages((current) => [
      ...current,
      { id: crypto.randomUUID(), role: 'user', text: trimmedGitUrl }
    ]);
    setGitUrl('');
    setIsSubmitting(true);

    try {
      const response = await fetch(`${apiBaseUrl}/api/conversations`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({ gitUrl: trimmedGitUrl })
      });

      if (!response.ok) {
        throw new Error('The backend could not save this Git URL.');
      }

      const conversation: ConversationResponse = await response.json();
      setConversationId(conversation.id);
      setMessages((current) => [
        ...current,
        { id: crypto.randomUUID(), role: 'assistant', text: conversation.assistantMessage }
      ]);

      const cloneResponse = await fetch(`${apiBaseUrl}/api/repositories/clone`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({
          gitUrl: trimmedGitUrl,
          branch: branch.trim() || null,
          conversationId: conversation.id
        })
      });

      if (!cloneResponse.ok) {
        const errorBody = await cloneResponse.json().catch(() => null);
        throw new Error(errorBody?.message ?? 'The backend could not clone this repository.');
      }

      const clone: CloneRepositoryResponse = await cloneResponse.json();
      setMessages((current) => [
        ...current,
        {
          id: crypto.randomUUID(),
          role: 'assistant',
          text: `Repository cloned successfully to ${clone.localPath}.`
        }
      ]);
    } catch (error) {
      setMessages((current) => [
        ...current,
        {
          id: crypto.randomUUID(),
          role: 'assistant',
          text: error instanceof Error ? error.message : 'Something went wrong while saving the Git URL.'
        }
      ]);
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <main className="app-shell">
      <aside className="sidebar">
        <div className="brand">
          <div className="brand-icon"><GitBranch size={22} /></div>
          <div>
            <h1>AI Docker Agent</h1>
            <p>Sprint 1 workspace</p>
          </div>
        </div>
        <div className="status-panel">
          <span>Current step</span>
          <strong>Collect Git URL</strong>
        </div>
        <div className="status-panel">
          <span>Conversation</span>
          <strong>{conversationId ?? 'Not started'}</strong>
        </div>
        <div className="status-panel">
          <span>GitHub</span>
          <strong>{user?.authenticated ? user.login ?? 'Logged in' : 'Not logged in'}</strong>
        </div>
        <a className="login-button" href={`${apiBaseUrl}/oauth2/authorization/github`}>
          <Github size={18} />
          <span>{user?.authenticated ? 'Reconnect GitHub' : 'Login with GitHub'}</span>
        </a>
      </aside>

      <section className="chat-panel" aria-label="AI Docker Agent chat">
        <div className="messages">
          {messages.map((message) => (
            <article className={`message ${message.role}`} key={message.id}>
              <div className="avatar" aria-hidden="true">
                {message.role === 'assistant' ? <Bot size={18} /> : <UserRound size={18} />}
              </div>
              <p>{message.text}</p>
            </article>
          ))}
        </div>

        <form className="composer" onSubmit={submitGitUrl}>
          <div className="composer-fields">
            <input
              aria-label="Git URL"
              placeholder="https://github.com/org/spring-maven-service.git"
              value={gitUrl}
              onChange={(event) => setGitUrl(event.target.value)}
            />
            <input
              aria-label="Branch"
              placeholder="Branch optional"
              value={branch}
              onChange={(event) => setBranch(event.target.value)}
            />
          </div>
          <button type="submit" disabled={!canSubmit} aria-label="Send Git URL">
            {isSubmitting ? <Loader2 className="spin" size={20} /> : <SendHorizonal size={20} />}
          </button>
        </form>
      </section>
    </main>
  );
}

createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>
);
