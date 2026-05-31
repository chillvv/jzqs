export type SavedAdminCredentials = {
  phone: string;
  password: string;
  remember: true;
};

export type AdminLoginFormState = {
  phone: string;
  password: string;
  remember: boolean;
};

export type AdminAuthSession = {
  token: string;
  userId: number;
  displayName: string;
  phone: string;
  role: string;
};

export const ADMIN_AUTH_STORAGE_KEY = "jzqs_admin_auth";
export const ADMIN_CREDENTIALS_STORAGE_KEY = "jzqs_admin_credentials";

export function buildInitialAdminLoginForm(saved: SavedAdminCredentials | null): AdminLoginFormState {
  if (!saved) {
    return {
      phone: "",
      password: "",
      remember: false
    };
  }
  return {
    phone: saved.phone,
    password: saved.password,
    remember: true
  };
}

export function buildSavedAdminCredentials(input: {
  phone: string;
  password: string;
  remember: boolean;
}): SavedAdminCredentials | null {
  if (!input.remember) {
    return null;
  }
  return {
    phone: input.phone.trim(),
    password: input.password,
    remember: true
  };
}

export function parseSavedAdminCredentials(rawValue: string | null | undefined): SavedAdminCredentials | null {
  const parsed = parseJson(rawValue);
  if (!parsed || typeof parsed !== "object") {
    return null;
  }
  const phone = normalizeText((parsed as Record<string, unknown>).phone);
  const password = typeof (parsed as Record<string, unknown>).password === "string"
    ? (parsed as Record<string, unknown>).password
    : "";
  const remember = (parsed as Record<string, unknown>).remember === true;
  if (!phone || !password || !remember) {
    return null;
  }
  return {
    phone,
    password,
    remember: true
  };
}

export function buildAdminAuthSession(input: AdminAuthSession): AdminAuthSession {
  return {
    token: normalizeText(input.token),
    userId: Number(input.userId),
    displayName: normalizeText(input.displayName),
    phone: normalizeText(input.phone),
    role: normalizeText(input.role)
  };
}

export function parseAdminAuthSession(rawValue: string | null | undefined): AdminAuthSession | null {
  const parsed = parseJson(rawValue);
  if (!parsed || typeof parsed !== "object") {
    return null;
  }
  const session = buildAdminAuthSession({
    token: String((parsed as Record<string, unknown>).token ?? ""),
    userId: Number((parsed as Record<string, unknown>).userId ?? 0),
    displayName: String((parsed as Record<string, unknown>).displayName ?? ""),
    phone: String((parsed as Record<string, unknown>).phone ?? ""),
    role: String((parsed as Record<string, unknown>).role ?? "")
  });
  if (!session.token || !Number.isFinite(session.userId) || session.userId <= 0 || !session.phone || !session.role) {
    return null;
  }
  return session;
}

function normalizeText(value: unknown) {
  return typeof value === "string" ? value.trim() : "";
}

function parseJson(rawValue: string | null | undefined): unknown {
  if (!rawValue) {
    return null;
  }
  try {
    return JSON.parse(rawValue);
  } catch {
    return null;
  }
}
