/**
 * Global FIFO mutex. settleTrade runs through enqueue() so only one trade
 * settles at a time — preventing two races when an agent is in two trades at
 * once: nonce collisions (same wallet, same nonce) and balance double-spends
 * (two checks pass before either tx lands).
 */
let tail = Promise.resolve();

/** Run `task` after all previously-enqueued tasks settle. */
export function enqueue(task) {
  const result = tail.then(task);
  // One task's rejection must not break the chain for the next.
  tail = result.then(() => undefined, () => undefined);
  return result;
}
