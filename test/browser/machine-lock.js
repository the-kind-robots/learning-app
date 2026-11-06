const fs = require('fs');
const os = require('os');
const path = require('path');

// One browser run at a time on this machine (#784). Every worktree's run
// starts its own Chrome workers, and two runs at once on one WSL machine
// turn a five-minute suite into an hour of reruns. A later run waits here
// until the earlier one ends.
//
// The lock is a directory in the shared tmp: mkdir is atomic, and the pid
// inside lets a waiter take over from a run that was killed outright.
// CI runs alone on its machine and skips the lock.

const LOCK = path.join(os.tmpdir(), 'learning-app-playwright.lock');
const OWNER = path.join(LOCK, 'owner.json');

function owner() {
  try {
    return JSON.parse(fs.readFileSync(OWNER, 'utf8'));
  } catch {
    return null;
  }
}

function alive(pid) {
  try {
    process.kill(pid, 0);
    return true;
  } catch (error) {
    return error.code === 'EPERM';
  }
}

function take() {
  try {
    fs.mkdirSync(LOCK);
  } catch (error) {
    if (error.code !== 'EEXIST') throw error;
    return false;
  }
  fs.writeFileSync(OWNER, JSON.stringify({ pid: process.pid, cwd: process.cwd() }));
  return true;
}

function release() {
  if (owner()?.pid === process.pid) fs.rmSync(LOCK, { recursive: true, force: true });
}

module.exports = async function machineLock() {
  if (process.env.CI) return;
  let announced = false;
  while (!take()) {
    const holder = owner();
    // A lock without a living owner is left by a killed run; a lock without
    // an owner file yet is one being taken this instant, so wait for it.
    if (holder && !alive(holder.pid)) {
      fs.rmSync(LOCK, { recursive: true, force: true });
      continue;
    }
    if (!announced) {
      console.log(`Waiting for the browser run in ${holder?.cwd ?? 'another worktree'} to finish (#784).`);
      announced = true;
    }
    await new Promise((resolve) => setTimeout(resolve, 2000));
  }
  process.on('exit', release);
  return release;
};
