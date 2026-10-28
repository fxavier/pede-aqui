import { importedScreens, findImportedScreen, type ImportedScreen } from "./generated-screens";

const shouldUseMocks = process.env.NEXT_PUBLIC_ENABLE_MOCKS !== "false";

function wait(ms: number) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

export type ScreenSummary = ImportedScreen & {
  status: "ready" | "api-ready";
};

export type ScreenTemplate = ImportedScreen & {
  html: string;
};

export async function listScreens(): Promise<ScreenSummary[]> {
  if (shouldUseMocks) {
    await wait(120);
    return importedScreens.map((screen) => ({ ...screen, status: "ready" }));
  }

  // Future API integration example:
  // return httpClient<ScreenSummary[]>("/backoffice/screens");
  return importedScreens.map((screen) => ({ ...screen, status: "api-ready" }));
}

export async function getScreen(slug: string): Promise<ImportedScreen> {
  if (shouldUseMocks) {
    await wait(80);
    const screen = findImportedScreen(slug);
    if (!screen) throw new Error(`Ecrã ${slug} não encontrado`);
    return screen;
  }

  // Future API integration example:
  // return httpClient<ImportedScreen>(`/backoffice/screens/${slug}`);
  const screen = findImportedScreen(slug);
  if (!screen) throw new Error(`Ecrã ${slug} não encontrado`);
  return screen;
}

export async function getScreenTemplate(slug: string): Promise<ScreenTemplate> {
  const screen = await getScreen(slug);
  const response = await fetch(screen.templateUrl);

  if (!response.ok) {
    throw new Error(`Falha ao carregar o template do ecrã ${screen.templateUrl}`);
  }

  return {
    ...screen,
    html: await response.text(),
  };
}
