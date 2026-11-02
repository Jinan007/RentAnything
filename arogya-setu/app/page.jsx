import Link from "next/link";

export default function Home() {
  return (
    <main className="min-h-screen flex flex-col items-center justify-center gap-4 p-6">
      <h1 className="text-4xl font-bold">Arogya-Setu</h1>
      <Link href="/clerk" className="rounded-lg bg-teal-600 px-6 py-3 text-white font-semibold">
        Clerk Dashboard
      </Link>
    </main>
  );
}
