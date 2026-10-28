import { notFound } from "next/navigation";
import { ImportedScreenView } from "@/features/screens/imported-screen-view";
import { findImportedScreen, importedScreens } from "@/features/screens/generated-screens";

export function generateStaticParams() {
  return importedScreens.map((screen) => ({ slug: screen.slug }));
}

export default async function ScreenPage({ params }: { params: Promise<{ slug: string }> }) {
  const { slug } = await params;
  const screen = findImportedScreen(slug);

  if (!screen) {
    notFound();
  }

  return <ImportedScreenView slug={slug} />;
}
