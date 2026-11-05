// Edit this list to change on-screen captions. Times are in seconds.
export type Caption = { start: number; end: number; text: string };

export const captions: Caption[] = [
  { start: 0.5, end: 3, text: "Welcome to RentAnything" },
  { start: 3.5, end: 6.5, text: "Rent anything, anywhere" },
];
