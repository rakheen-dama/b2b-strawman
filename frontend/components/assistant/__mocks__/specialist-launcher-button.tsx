/**
 * Vitest automatic mock for SpecialistLauncherButton.
 *
 * Any test that imports `@/components/assistant/specialist-launcher-button`
 * and calls `vi.mock("@/components/assistant/specialist-launcher-button")`
 * without a factory will pick this up automatically.
 *
 * Tests that need the factory-less form must add the vi.mock call; Vitest
 * does not auto-apply __mocks__ without an explicit vi.mock() declaration.
 * However, the factory is now centralised here rather than duplicated in
 * every test file.
 */
export function SpecialistLauncherButton() {
  return null;
}
