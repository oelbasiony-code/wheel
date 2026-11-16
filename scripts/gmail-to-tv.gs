/**
 * Gmail -> TV. Sends important new emails (just the sender's name and the subject) to Wheel
 * on the Fire TV, through the TV's private ntfy.sh topic.
 *
 * Runs in YOUR Google account at script.google.com, every 5 minutes. Nothing else leaves
 * Gmail: no email bodies, no attachments. Emails it sends get the Gmail label "TV" so they
 * are never sent twice.
 *
 * Setup (once):
 *  1. script.google.com -> New project -> delete what's there -> paste this whole file.
 *  2. Put your TV's topic (the part after https://ntfy.sh/) in TOPIC below. Keep it private.
 *  3. Save. Pick "setup" in the function menu at the top, press Run, and allow access.
 *     That sends a test to the TV and turns on the 5-minute check.
 *
 * To change what counts as important, edit SENDERS and WORDS below and save.
 */

const TOPIC = 'PASTE-YOUR-TOPIC-HERE';

// Emails from these count as important (a domain like "aamc.org", or "edu" for every school).
const SENDERS = ['edu', 'aamc.org', 'amcas'];

// Emails with any of these words in the subject count as important.
const WORDS = ['interview', 'admission', 'admissions', 'acceptance', 'accepted',
               'decision', 'waitlist', 'secondary', 'application'];

const LABEL = 'TV';

/** Run this once by hand: sends a test and starts the 5-minute check. */
function setup() {
  ScriptApp.getProjectTriggers().forEach(function (t) { ScriptApp.deleteTrigger(t); });
  ScriptApp.newTrigger('checkMail').timeBased().everyMinutes(5).create();
  sendToTv('Gmail', 'Gmail to TV is set up');
  checkMail();
}

/** Every 5 minutes: new matching emails from the last day that haven't been sent yet. */
function checkMail() {
  const label = GmailApp.getUserLabelByName(LABEL) || GmailApp.createLabel(LABEL);
  // Inside { } Gmail matches ANY of the terms.
  const from = SENDERS.map(function (s) { return 'from:' + quote(s); }).join(' ');
  const subj = WORDS.map(function (w) { return 'subject:' + quote(w); }).join(' ');
  const query = 'in:inbox newer_than:1d -label:' + LABEL + ' {' + from + ' ' + subj + '}';

  GmailApp.search(query, 0, 10).forEach(function (thread) {
    const msg = thread.getMessages().pop();   // the newest email in the conversation
    sendToTv(senderName(msg.getFrom()), msg.getSubject() || '(no subject)');
    thread.addLabel(label);
  });
}

function sendToTv(title, text) {
  if (!TOPIC || TOPIC.indexOf('PASTE') === 0) throw new Error('Put your TV topic in TOPIC first.');
  UrlFetchApp.fetch('https://ntfy.sh/' + encodeURIComponent(TOPIC), {
    method: 'post',
    payload: text,
    headers: { Title: ascii(title) },
  });
}

/** "Harvard Admissions <admissions@hms.harvard.edu>" -> "Harvard Admissions". */
function senderName(from) {
  const m = from.match(/^\s*"?([^"<]+?)"?\s*</);
  return m ? m[1] : from.replace(/[<>]/g, '');
}

function quote(s) {
  return /\s/.test(s) ? '"' + s + '"' : s;
}

/** The TV's dot font only has plain letters anyway; keeps the header safe. */
function ascii(s) {
  return s.normalize('NFKD').replace(/[^\x20-\x7E]/g, '').trim() || 'Email';
}
