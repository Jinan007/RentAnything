"use client";

import { useState } from "react";
import { addDoc, collection, serverTimestamp } from "firebase/firestore";
import QRCode from "react-qr-code";
import { ClipboardPlus, Loader2 } from "lucide-react";
import { db } from "@/lib/firebase";

const TIMINGS = ["Morning", "Night", "Morning & Night"];

export default function ClerkPage() {
  const [opNumber, setOpNumber] = useState("");
  const [medicine, setMedicine] = useState("");
  const [timing, setTiming] = useState(TIMINGS[0]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [patientUrl, setPatientUrl] = useState("");

  async function handleSubmit(e) {
    e.preventDefault();
    setLoading(true);
    setError("");
    try {
      const ref = await addDoc(collection(db, "queue"), {
        opNumber: opNumber.trim(),
        medicineName: medicine.trim(),
        dosageTiming: timing,
        status: "waiting",
        createdAt: serverTimestamp(),
      });
      const base = process.env.NEXT_PUBLIC_APP_URL || window.location.origin;
      setPatientUrl(`${base}/patient/${ref.id}`);
    } catch (err) {
      console.error(err);
      setError("Could not add to queue. Check Firebase config and rules.");
    } finally {
      setLoading(false);
    }
  }

  function reset() {
    setPatientUrl("");
    setOpNumber("");
    setMedicine("");
    setTiming(TIMINGS[0]);
  }

  const inputClass =
    "w-full rounded-lg border border-slate-300 bg-white px-4 py-3 text-lg focus:outline-none focus:ring-2 focus:ring-teal-500";

  return (
    <main className="min-h-screen p-8">
      <div className="mx-auto max-w-4xl">
        <h1 className="mb-8 flex items-center gap-3 text-3xl font-bold">
          <ClipboardPlus className="h-8 w-8 text-teal-600" /> Clerk Dashboard
        </h1>

        <div className="grid gap-8 md:grid-cols-2">
          <form onSubmit={handleSubmit} className="space-y-5 rounded-2xl bg-white p-6 shadow">
            <div>
              <label className="mb-1 block font-semibold" htmlFor="op">Patient OP Number</label>
              <input id="op" required value={opNumber} onChange={(e) => setOpNumber(e.target.value)} className={inputClass} />
            </div>
            <div>
              <label className="mb-1 block font-semibold" htmlFor="med">Medicine Name</label>
              <input id="med" required value={medicine} onChange={(e) => setMedicine(e.target.value)} className={inputClass} />
            </div>
            <div>
              <label className="mb-1 block font-semibold" htmlFor="timing">Dosage Timing</label>
              <select id="timing" value={timing} onChange={(e) => setTiming(e.target.value)} className={inputClass}>
                {TIMINGS.map((t) => <option key={t}>{t}</option>)}
              </select>
            </div>
            {error && <p className="text-red-600">{error}</p>}
            <button
              type="submit"
              disabled={loading}
              className="flex w-full items-center justify-center gap-2 rounded-lg bg-teal-600 px-6 py-3 text-lg font-semibold text-white hover:bg-teal-700 disabled:opacity-60"
            >
              {loading && <Loader2 className="h-5 w-5 animate-spin" />}
              Generate &amp; Add to Queue
            </button>
          </form>

          <div className="flex flex-col items-center justify-center rounded-2xl bg-white p-6 shadow">
            {patientUrl ? (
              <>
                <div className="rounded-xl bg-white p-4 ring-1 ring-slate-200">
                  <QRCode value={patientUrl} size={240} />
                </div>
                <p className="mt-4 break-all text-center text-sm text-slate-500">{patientUrl}</p>
                <button onClick={reset} className="mt-4 rounded-lg border border-slate-300 px-4 py-2 font-medium hover:bg-slate-100">
                  Next patient
                </button>
              </>
            ) : (
              <p className="text-slate-400">QR code will appear here</p>
            )}
          </div>
        </div>
      </div>
    </main>
  );
}
