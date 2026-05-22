import React, { FormEvent, useEffect, useMemo, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { Bot, Check, Github, GitBranch, Loader2, SendHorizonal, UserRound, X } from 'lucide-react';
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
  branch: string | null;
  localPath: string;
  repositoryAnalysisId: string | null;
  repositoryAnalysisPath: string | null;
  executableModuleCandidates: string[];
  status: string;
  nextAction: string | null;
  assistantMessage: string;
};

type AnalysisResponse = {
  repositoryAnalysisId: string;
  analysisArtifactPath: string;
  executableModuleCandidates: string[];
  selectedExecutableModules: string[];
  status: string;
  assistantMessage: string;
};

type PermissionCheckPullRequestResponse = {
  repositoryWorkspaceId: string;
  deploymentBranch: string;
  commitId: string;
  pullRequestUrl: string | null;
  pullRequestNumber: number | null;
  status: string;
  assistantMessage: string;
};

type DockerConfigsResponse = {
  repositoryWorkspaceId: string;
  generatedFiles: string[];
  deploymentBranch: string;
  commitId: string;
  pullRequestUrl: string | null;
  pullRequestNumber: number | null;
  status: string;
  assistantMessage: string;
};

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:9040';

const initialMessages: Message[] = [
  {
    id: 'welcome',
    role: 'assistant',
    text: 'Hi, I am your AI Docker Agent. Login with GitHub, then share an HTTPS Git URL for a Java (Maven/Gradle) or Node.js (NPM) project that I can read.'
  }
];

function App() {
  const [messages, setMessages] = useState<Message[]>(initialMessages);
  const [gitUrl, setGitUrl] = useState('');
  const [branch, setBranch] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isCreatingPr, setIsCreatingPr] = useState(false);
  const [isGeneratingDockerConfigs, setIsGeneratingDockerConfigs] = useState(false);
  const [conversationId, setConversationId] = useState<string | null>(null);
  const [workflowStatus, setWorkflowStatus] = useState('WAITING_FOR_GIT_URL');
  const [repositoryWorkspaceId, setRepositoryWorkspaceId] = useState<string | null>(null);
  const [repositoryAnalysisId, setRepositoryAnalysisId] = useState<string | null>(null);
  const [clonedBaseBranch, setClonedBaseBranch] = useState<string | null>(null);
  const [showPermissionCheckPrompt, setShowPermissionCheckPrompt] = useState(false);
  const [showDockerConfigPrompt, setShowDockerConfigPrompt] = useState(false);
  const [executableModuleCandidates, setExecutableModuleCandidates] = useState<string[]>([]);
  const [selectedExecutableModules, setSelectedExecutableModules] = useState<string[]>([]);
  const [isSavingModules, setIsSavingModules] = useState(false);
  const [debugRecords, setDebugRecords] = useState<string | null>(null);
  const [isLoadingDebug, setIsLoadingDebug] = useState(false);
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
    setWorkflowStatus('GIT_URL_RECEIVED');
    setShowPermissionCheckPrompt(false);
    setShowDockerConfigPrompt(false);
    setRepositoryWorkspaceId(null);
    setRepositoryAnalysisId(null);
    setExecutableModuleCandidates([]);
    setSelectedExecutableModules([]);
    setDebugRecords(null);
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
      setWorkflowStatus(conversation.status);
      setMessages((current) => [
        ...current,
        { id: crypto.randomUUID(), role: 'assistant', text: conversation.assistantMessage }
      ]);

      setWorkflowStatus('CLONING');
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
      setWorkflowStatus(clone.status);
      setRepositoryWorkspaceId(clone.repositoryWorkspaceId);
      setRepositoryAnalysisId(clone.repositoryAnalysisId);
      setClonedBaseBranch(clone.branch);
      setExecutableModuleCandidates(clone.executableModuleCandidates ?? []);
      setSelectedExecutableModules(clone.executableModuleCandidates ?? []);
      setShowPermissionCheckPrompt(clone.nextAction === 'ASK_PULL_REQUEST_PERMISSION_CHECK');
      setMessages((current) => [
        ...current,
        {
          id: crypto.randomUUID(),
          role: 'assistant',
          text: clone.assistantMessage
        }
      ]);
      if (clone.nextAction === 'ASK_EXECUTABLE_MODULES') {
        setMessages((current) => [
          ...current,
          {
            id: crypto.randomUUID(),
            role: 'assistant',
            text: 'Which project modules should be treated as executable services for Docker, CI/CD, and Kubernetes generation?'
          }
        ]);
      }
    } catch (error) {
      setWorkflowStatus('ERROR');
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

  async function createPermissionCheckPullRequest() {
    if (!repositoryWorkspaceId) {
      return;
    }

    setShowPermissionCheckPrompt(false);
    setIsCreatingPr(true);
    setWorkflowStatus('CREATING_PERMISSION_CHECK_PR');
    setMessages((current) => [
      ...current,
      { id: crypto.randomUUID(), role: 'user', text: 'Yes, create the permission check pull request.' }
    ]);

    try {
      const response = await fetch(`${apiBaseUrl}/api/deployment-permissions/pull-request-check`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({
          repositoryWorkspaceId,
          baseBranch: clonedBaseBranch ?? undefined
        })
      });

      if (!response.ok) {
        const errorBody = await response.json().catch(() => null);
        throw new Error(errorBody?.message ?? 'The backend could not create the permission check pull request.');
      }

      const pullRequest: PermissionCheckPullRequestResponse = await response.json();
      setWorkflowStatus(pullRequest.status);
      setShowDockerConfigPrompt(true);
      setMessages((current) => [
        ...current,
        {
          id: crypto.randomUUID(),
          role: 'assistant',
          text: pullRequest.assistantMessage
        },
        {
          id: crypto.randomUUID(),
          role: 'assistant',
          text: 'Do you want me to generate Dockerfile, .dockerignore, .env.example, docker-compose.yml, and README_DEPLOY.md, review them with local deepseek-coder, then create a pull request with those files?'
        }
      ]);
    } catch (error) {
      setWorkflowStatus('PR_FAILED');
      setMessages((current) => [
        ...current,
        {
          id: crypto.randomUUID(),
          role: 'assistant',
          text: error instanceof Error ? error.message : 'Something went wrong while creating the permission check pull request.'
        }
      ]);
    } finally {
      setIsCreatingPr(false);
    }
  }

  async function generateDockerConfigs() {
    if (!repositoryWorkspaceId) {
      return;
    }

    setShowDockerConfigPrompt(false);
    setIsGeneratingDockerConfigs(true);
    setWorkflowStatus('GENERATING_DOCKER_CONFIGS');
    setMessages((current) => [
      ...current,
      { id: crypto.randomUUID(), role: 'user', text: 'Yes, generate the Docker deployment configuration, review it locally, and create a pull request.' }
    ]);

    try {
      const response = await fetch(`${apiBaseUrl}/api/deployment-permissions/docker-configs`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({ repositoryWorkspaceId })
      });

      if (!response.ok) {
        const errorBody = await response.json().catch(() => null);
        throw new Error(errorBody?.message ?? 'The backend could not generate Docker deployment files.');
      }

      const dockerConfigs: DockerConfigsResponse = await response.json();
      setWorkflowStatus(dockerConfigs.status);
      setMessages((current) => [
        ...current,
        {
          id: crypto.randomUUID(),
          role: 'assistant',
          text: dockerConfigs.assistantMessage
        }
      ]);
    } catch (error) {
      setWorkflowStatus('DOCKER_CONFIGS_FAILED');
      setMessages((current) => [
        ...current,
        {
          id: crypto.randomUUID(),
          role: 'assistant',
          text: error instanceof Error ? error.message : 'Something went wrong while generating Docker deployment files.'
        }
      ]);
    } finally {
      setIsGeneratingDockerConfigs(false);
    }
  }

  async function saveExecutableModules() {
    if (!repositoryAnalysisId || selectedExecutableModules.length === 0) {
      return;
    }

    setIsSavingModules(true);
    try {
      const response = await fetch(`${apiBaseUrl}/api/analysis/${repositoryAnalysisId}/executable-modules`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({ executableModules: selectedExecutableModules })
      });

      if (!response.ok) {
        const errorBody = await response.json().catch(() => null);
        throw new Error(errorBody?.message ?? 'The backend could not save executable modules.');
      }

      const analysis: AnalysisResponse = await response.json();
      setWorkflowStatus(analysis.status);
      setExecutableModuleCandidates([]);
      setMessages((current) => [
        ...current,
        { id: crypto.randomUUID(), role: 'user', text: `Executable modules: ${analysis.selectedExecutableModules.join(', ')}` },
        { id: crypto.randomUUID(), role: 'assistant', text: analysis.assistantMessage }
      ]);
      setShowPermissionCheckPrompt(true);
    } catch (error) {
      setMessages((current) => [
        ...current,
        {
          id: crypto.randomUUID(),
          role: 'assistant',
          text: error instanceof Error ? error.message : 'Something went wrong while saving executable modules.'
        }
      ]);
    } finally {
      setIsSavingModules(false);
    }
  }

  function toggleExecutableModule(modulePath: string) {
    setSelectedExecutableModules((current) =>
      current.includes(modulePath)
        ? current.filter((candidate) => candidate !== modulePath)
        : [...current, modulePath]
    );
  }

  async function loadDebugRecords() {
    if (!conversationId) {
      return;
    }

    setIsLoadingDebug(true);
    try {
      const response = await fetch(`${apiBaseUrl}/api/debug/conversations/${conversationId}`, {
        credentials: 'include'
      });
      if (!response.ok) {
        throw new Error('Unable to load debug records.');
      }
      const records = await response.json();
      setDebugRecords(JSON.stringify(records, null, 2));
    } catch (error) {
      setDebugRecords(error instanceof Error ? error.message : 'Unable to load debug records.');
    } finally {
      setIsLoadingDebug(false);
    }
  }

  function skipPermissionCheckPullRequest() {
    setShowPermissionCheckPrompt(false);
    setShowDockerConfigPrompt(true);
    setMessages((current) => [
      ...current,
      { id: crypto.randomUUID(), role: 'user', text: 'No, skip the permission check pull request for now.' },
      {
        id: crypto.randomUUID(),
        role: 'assistant',
        text: 'Permission check skipped.\nDo you want me to generate Dockerfile, .dockerignore, .env.example, docker-compose.yml, and README_DEPLOY.md, review them with local deepseek-coder, then create a pull request with those files?'
      }
    ]);
  }

  function skipDockerConfigs() {
    setShowDockerConfigPrompt(false);
    setMessages((current) => [
      ...current,
      { id: crypto.randomUUID(), role: 'user', text: 'No, do not generate Docker configs yet.' },
      {
        id: crypto.randomUUID(),
        role: 'assistant',
        text: 'Docker config generation skipped. I can generate Dockerfile, .dockerignore, .env.example, docker-compose.yml, and README_DEPLOY.md, review them with local deepseek-coder, then open a pull request when you are ready.'
      }
    ]);
  }

  function renderMessageText(text: string) {
    return text.split('\n').map((line, index) => (
      <React.Fragment key={`${line}-${index}`}>
        {line.startsWith('http://') || line.startsWith('https://') ? (
          <a href={line} target="_blank" rel="noreferrer">{line}</a>
        ) : (
          line
        )}
        {index < text.split('\n').length - 1 ? <br /> : null}
      </React.Fragment>
    ));
  }

  return (
    <main className="app-shell">
      <aside className="sidebar">
        <div className="brand">
          <div className="brand-icon"><GitBranch size={22} /></div>
          <div>
            <h1>AI Docker Agent</h1>
            <p>Deployment workspace</p>
          </div>
        </div>
        <div className="status-panel">
          <span>Current step</span>
          <strong>{workflowStatus}</strong>
        </div>
        <div className="status-panel">
          <span>Conversation</span>
          <strong>{conversationId ?? 'Not started'}</strong>
        </div>
        <div className="status-panel">
          <span>GitHub</span>
          <strong>{user?.authenticated ? user.login ?? 'Logged in' : 'Not logged in'}</strong>
        </div>
        <div className="status-panel">
          <span>Workspace</span>
          <strong>{repositoryWorkspaceId ?? 'Not cloned'}</strong>
        </div>
        <a className="login-button" href={`${apiBaseUrl}/oauth2/authorization/github`}>
          <Github size={18} />
          <span>{user?.authenticated ? 'Reconnect GitHub' : 'Login with GitHub'}</span>
        </a>
        {conversationId && (
          <button className="debug-button" type="button" onClick={loadDebugRecords}>
            {isLoadingDebug ? 'Loading records...' : 'Debug records'}
          </button>
        )}
      </aside>

      <section className="chat-panel" aria-label="AI Docker Agent chat">
        <div className="messages">
          {messages.map((message) => (
            <article className={`message ${message.role}`} key={message.id}>
              <div className="avatar" aria-hidden="true">
                {message.role === 'assistant' ? <Bot size={18} /> : <UserRound size={18} />}
              </div>
              <p>{renderMessageText(message.text)}</p>
            </article>
          ))}
          {showPermissionCheckPrompt && (
            <div className="action-row" aria-label="Permission check pull request confirmation">
              <button type="button" onClick={createPermissionCheckPullRequest} disabled={isCreatingPr}>
                {isCreatingPr ? <Loader2 className="spin" size={18} /> : <Check size={18} />}
                <span>Create permission check PR</span>
              </button>
              <button type="button" className="secondary" onClick={skipPermissionCheckPullRequest} disabled={isCreatingPr}>
                <X size={18} />
                <span>Skip</span>
              </button>
            </div>
          )}
          {showDockerConfigPrompt && (
            <div className="action-row" aria-label="Docker config generation confirmation">
              <button type="button" onClick={generateDockerConfigs} disabled={isGeneratingDockerConfigs}>
                {isGeneratingDockerConfigs ? <Loader2 className="spin" size={18} /> : <Check size={18} />}
                <span>Generate, review, and PR</span>
              </button>
              <button type="button" className="secondary" onClick={skipDockerConfigs} disabled={isGeneratingDockerConfigs}>
                <X size={18} />
                <span>Skip</span>
              </button>
            </div>
          )}
          {executableModuleCandidates.length > 0 && (
            <div className="module-picker" aria-label="Executable module selection">
              {executableModuleCandidates.map((modulePath) => (
                <label key={modulePath}>
                  <input
                    type="checkbox"
                    checked={selectedExecutableModules.includes(modulePath)}
                    onChange={() => toggleExecutableModule(modulePath)}
                  />
                  <span>{modulePath}</span>
                </label>
              ))}
              <button type="button" onClick={saveExecutableModules} disabled={isSavingModules || selectedExecutableModules.length === 0}>
                {isSavingModules ? <Loader2 className="spin" size={18} /> : <Check size={18} />}
                <span>Confirm modules</span>
              </button>
            </div>
          )}
          {debugRecords && (
            <section className="debug-panel" aria-label="Conversation Mongo records">
              <button type="button" onClick={() => setDebugRecords(null)}>Close debug</button>
              <pre>{debugRecords}</pre>
            </section>
          )}
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
