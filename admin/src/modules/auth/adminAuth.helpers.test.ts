import { describe, expect, it } from "vitest";
import {
  buildInitialAdminLoginForm,
  buildAdminAuthSession,
  buildSavedAdminCredentials,
  parseAdminAuthSession,
  parseSavedAdminCredentials
} from "./adminAuth.helpers";

describe("buildSavedAdminCredentials", () => {
  it("persists remembered admin credentials", () => {
    expect(
      buildSavedAdminCredentials({
        phone: "17671863805",
        password: "17671863805",
        remember: true
      })
    ).toEqual({
      phone: "17671863805",
      password: "17671863805",
      remember: true
    });
  });

  it("returns null when remember mode is disabled", () => {
    expect(
      buildSavedAdminCredentials({
        phone: "17671863805",
        password: "17671863805",
        remember: false
      })
    ).toBeNull();
  });

  it("parses persisted remembered credentials", () => {
    expect(
      parseSavedAdminCredentials(JSON.stringify({
        phone: "17671863805",
        password: "17671863805",
        remember: true
      }))
    ).toEqual({
      phone: "17671863805",
      password: "17671863805",
      remember: true
    });
  });

  it("returns null for malformed remembered credentials payload", () => {
    expect(parseSavedAdminCredentials("{bad-json")).toBeNull();
    expect(parseSavedAdminCredentials(JSON.stringify({
      phone: "",
      password: "17671863805",
      remember: true
    }))).toBeNull();
  });
});

describe("buildInitialAdminLoginForm", () => {
  it("returns an empty form when no remembered credentials exist", () => {
    expect(buildInitialAdminLoginForm(null)).toEqual({
      phone: "",
      password: "",
      remember: false
    });
  });

  it("rehydrates the remembered credentials only after they were previously saved", () => {
    expect(buildInitialAdminLoginForm({
      phone: "17671863805",
      password: "17671863805",
      remember: true
    })).toEqual({
      phone: "17671863805",
      password: "17671863805",
      remember: true
    });
  });
});

describe("buildAdminAuthSession", () => {
  it("normalizes the admin session payload returned by login", () => {
    expect(
      buildAdminAuthSession({
        token: "token-1",
        userId: 1,
        displayName: "商家后台",
        phone: " 17671863805 ",
        role: "OWNER"
      })
    ).toEqual({
      token: "token-1",
      userId: 1,
      displayName: "商家后台",
      phone: "17671863805",
      role: "OWNER"
    });
  });

  it("parses a valid persisted admin session", () => {
    expect(
      parseAdminAuthSession(JSON.stringify({
        token: "token-1",
        userId: 1,
        displayName: "商家后台",
        phone: "17671863805",
        role: "OWNER"
      }))
    ).toEqual({
      token: "token-1",
      userId: 1,
      displayName: "商家后台",
      phone: "17671863805",
      role: "OWNER"
    });
  });

  it("rejects persisted session payload without token", () => {
    expect(parseAdminAuthSession(JSON.stringify({
      userId: 1,
      displayName: "商家后台",
      phone: "17671863805",
      role: "OWNER"
    }))).toBeNull();
  });
});
