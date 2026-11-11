/* 읽어주기 이어 주기: 안드로이드 WebView에는 speechSynthesis(읽어주기)가 없거나 소리가 안 난다.
   게임이 쓰는 speechSynthesis·SpeechSynthesisUtterance를 폰의 음성 엔진(NativeTTS, 자바 TextToSpeech)으로 바꿔 끼운다.
   게임 파일은 고치지 않는다. 앱이 띄울 때 이 글을 게임 앞에 넣는다(MainActivity.page). */
(function () {
  if (!window.NativeTTS) return;
  function U(text) {
    this.text = text == null ? "" : String(text);
    this.lang = "ko-KR"; this.rate = 1; this.pitch = 1; this.volume = 1; this.voice = null;
    this.onstart = null; this.onend = null; this.onerror = null;
  }
  var cur = null;
  var voice = { name: "Android", lang: "ko-KR", voiceURI: "android", localService: true, default: true };
  var ss = {
    speaking: false, pending: false, paused: false, onvoiceschanged: null,
    speak: function (u) {
      if (!u) return;
      cur = u; ss.speaking = true;
      try { NativeTTS.speak(String(u.text), String(u.lang || "ko-KR"), +u.rate || 1, +u.pitch || 1); } catch (e) {}
    },
    cancel: function () { cur = null; ss.speaking = false; try { NativeTTS.stop(); } catch (e) {} },
    pause: function () {}, resume: function () {},
    getVoices: function () { return [voice]; },
    addEventListener: function () {}, removeEventListener: function () {}
  };
  window.__ttsDone = function () {
    ss.speaking = false;
    var u = cur; cur = null;
    if (u && typeof u.onend === "function") { try { u.onend({ utterance: u }); } catch (e) {} }
  };
  try { Object.defineProperty(window, "speechSynthesis", { value: ss, configurable: true, writable: true }); } catch (e) {}
  try { Object.defineProperty(window, "SpeechSynthesisUtterance", { value: U, configurable: true, writable: true }); } catch (e) {}
})();
