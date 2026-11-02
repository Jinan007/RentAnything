"use client";

import { useEffect, useState } from "react";
import { useParams } from "next/navigation";
import { collection, doc, onSnapshot, query, where } from "firebase/firestore";
import { Moon, Sun, Users, Volume2 } from "lucide-react";
import { db } from "@/lib/firebase";

export default function PatientPage() {
  const { id } = useParams();
  const [patient, setPatient] = useState(null);
  const [notFound, setNotFound] = useState(false);
  const [waiting, setWaiting] = useState(null); // array of {id, createdAt}

  // Live patient document
  useEffect(() => {
    if (!id) return;
    return onSnapshot(
      doc(db, "queue", id),
      (snap) => {
        if (snap.exists()) setPatient({ id: snap.id, ...snap.data() });
        else setNotFound(true);
      },
      () => setNotFound(true)
    );
  }, [id]);

  // Live list of all waiting patients
  useEffect(() => {
    const q = query(collection(db, "queue"), where("status", "==", "waiting"));
    return onSnapshot(q, (snap) =>
      setWaiting(
        snap.docs.map((d) => ({
          id: d.id,
          ms: d.data().createdAt?.toMillis?.() ?? Date.now(),
        }))
      )
    );
  }, []);

  function speak() {
    if (!patient || !("speechSynthesis" in window)) return;
    const text = `Take ${patient.medicineName}, ${patient.dosageTiming}`;
    const synth = window.speechSynthesis;
    synth.cancel();
    const u = new SpeechSynthesisUtterance(text);
    const voices = synth.getVoices();
    const hasMl = voices.some((v) => v.lang?.toLowerCase().startsWith("ml"));
    u.lang = hasMl ? "ml-IN" : "en-IN"; // fallback to English if no Malayalam voice
    u.rate = 0.85;
    synth.speak(u);
  }

  if (notFound) {
    return <main className="flex min-h-screen items-center justify-center p-6 text-2xl font-bold">Prescription not found.</main>;
  }
  if (!patient || waiting === null) {
    return <main className="flex min-h-screen items-center justify-center p-6 text-2xl">Loading…</main>;
  }

  // Patients ahead = waiting patients created before this one (0 if already called)
  const myMs = patient.createdAt?.toMillis?.() ?? Date.now();
  const ahead =
    patient.status === "waiting"
      ? waiting.filter((w) => w.id !== patient.id && w.ms < myMs).length
      : 0;

  const t = patient.dosageTiming;
  const showSun = t.includes("Morning");
  const showMoon = t.includes("Night");

  return (
    <main className="mx-auto flex min-h-screen max-w-md flex-col gap-6 p-4">
      <section className="rounded-3xl bg-slate-900 p-6 text-center text-white">
        <Users className="mx-auto mb-2 h-10 w-10" aria-hidden />
        <p className="text-xl font-semibold">Live OP Queue</p>
        <p className="mt-3 text-lg">Total Patients Ahead of You</p>
        <p className="text-7xl font-extrabold leading-tight" aria-live="polite">{ahead}</p>
      </section>

      <section className="rounded-3xl bg-white p-6 text-center shadow">
        <p className="text-lg text-slate-600">Your medicine</p>
        <p className="mt-1 text-4xl font-extrabold">{patient.medicineName}</p>
        <div className="my-5 flex items-center justify-center gap-6">
          {showSun && <Sun className="h-28 w-28 text-amber-500" aria-label="Morning" />}
          {showMoon && <Moon className="h-28 w-28 text-indigo-600" aria-label="Night" />}
        </div>
        <p className="text-3xl font-bold">{t}</p>
      </section>

      <button
        onClick={speak}
        className="flex w-full flex-col items-center justify-center gap-3 rounded-3xl bg-yellow-400 px-6 py-10 text-3xl font-extrabold text-black shadow-lg ring-4 ring-black active:bg-yellow-300"
      >
        <Volume2 className="h-16 w-16" aria-hidden />
        Play Audio Instructions
      </button>
    </main>
  );
}
