import React, { FormEvent, useMemo, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { Bot, GitBranch, Loader2, SendHorizonal, UserRound } from 'lucide-react';
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

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:9040';

const initialMessages: Message[] = [
  {
    id: 'welcome',
    role: 'assistant',
    text: 'Hi, I am your AI Docker Agent. Share a Git URL for a Java Spring Maven project that I can read.'
  }
];

function App() {
  const [messages, setMessages] = useState<Message[]>(initialMessages);
  const [gitUrl, setGitUrl] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [conversationId, setConversationId] = useState<string | null>(null);
  const canSubmit = useMemo(() => gitUrl.trim().length > 0 && !isSubmitting, [gitUrl, isSubmitting]);

  async function submitGitUrl(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!canSubmit) {
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
          <input
            aria-label="Git URL"
            placeholder="https://github.com/org/spring-maven-service.git"
            value={gitUrl}
            onChange={(event) => setGitUrl(event.target.value)}
          />
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
