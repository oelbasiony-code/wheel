/**
 * Wheel: new emails on the TV as "Sender Name: Subject".
 *
 * Runs in your own Google account (script.google.com), once a minute. For each new email in
 * the inbox it sends a short email to the TV's ntfy address; ntfy turns the subject into the
 * message. (ntfy's email address drops who an email is from, so plain Gmail forwarding can't
 * show the sender; this script puts the name in the subject. It sends email rather than
 * calling ntfy.sh over the web, because Google's script servers can't reach ntfy.sh directly.)
 *
 * Setup (once):
 *  1. script.google.com > New project, signed in to the Gmail account to watch. Paste this file.
 *  2. Project Settings (gear) > Script Properties > Add: TV_ADDRESS = ntfy-YOUR_TOPIC@ntfy.sh
 *     (the TV's private address; it's not in this file because the repo is public).
 *  3. Pick "setup" in the function menu, Run, and allow the permissions.
 *  4. Turn off Gmail forwarding to the TV address, or every email shows up twice.
 */

function setup() {
  ScriptApp.getProjectTriggers().forEach(function (t) { ScriptApp.deleteTrigger(t); });
  ScriptApp.newTrigger('check').timeBased().everyMinutes(1).create();
  // Start from now: older emails aren't sent.
  PropertiesService.getScriptProperties().setProperty('SINCE', String(Date.now()));
}

function check() {
  var props = PropertiesService.getScriptProperties();
  var to = props.getProperty('TV_ADDRESS');
  if (!to) return;
  var since = Number(props.getProperty('SINCE') || Date.now());
  var newest = since;

  var found = [];
  GmailApp.search('in:inbox newer_than:1d', 0, 25).forEach(function (thread) {
    thread.getMessages().forEach(function (m) {
      var t = m.getDate().getTime();
      if (t > since) {
        found.push(m);
        if (t > newest) newest = t;
      }
    });
  });
  found.sort(function (a, b) { return a.getDate() - b.getDate(); });

  found.forEach(function (m) {
    var from = m.getFrom();                     // "Jack Westin <jack@example.com>"
    var name = from.replace(/<[^>]*>/, '').replace(/"/g, '').trim();
    if (!name) name = from.replace(/[<>]/g, '').trim();
    var subject = (m.getSubject() || '(no subject)').trim();
    // The body is a marker only: Wheel sees a line break (so it's MAIL, not MSG) and stops
    // at "--", so the TV shows just "Jack Westin: Subject".
    MailApp.sendEmail(to, name + ': ' + subject, '--\nwheel');
  });

  props.setProperty('SINCE', String(newest));
}
