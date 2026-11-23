// UI texts. Polish is the default; the choice is remembered per browser.
const DICT = {
  pl: {
    menu: 'Menu', child: 'Dziecko', language: 'Język',
    login: 'Zaloguj', username: 'Użytkownik', password: 'Hasło', loginFailed: 'Nieprawidłowy login lub hasło (lub zbyt wiele prób).',
    logout: 'Wyloguj',
    dashboard: 'Pulpit', grades: 'Oceny', attendance: 'Frekwencja', timetable: 'Plan lekcji', calendar: 'Terminarz',
    homework: 'Zadania domowe', messages: 'Wiadomości', announcements: 'Ogłoszenia', behaviour: 'Uwagi i zachowanie',
    settings: 'Ustawienia',
    today: 'Dziś', tomorrow: 'Jutro', lessonsOn: 'Lekcje', upcoming: 'Nadchodzące (7 dni)', recent: 'Ostatnie zmiany',
    luckyNumber: 'Szczęśliwy numerek', unread: 'Nieprzeczytane wiadomości', nothing: 'Brak danych.',
    noChildren: 'Brak skonfigurowanych kont Librus. Ustaw LIBRUS_ACCOUNT_1_LOGIN i LIBRUS_ACCOUNT_1_PASSWORD.',
    average: 'Średnia', semester1: 'Semestr I', semester2: 'Semestr II', summaryGrades: 'Oceny śródroczne / roczne',
    weight: 'waga', category: 'kategoria', teacher: 'nauczyciel', comment: 'komentarz',
    presence: 'Obecność', lessons: 'lekcji', absences: 'Nieobecności i spóźnienia',
    kind_PRESENT: 'obecny', kind_LATE: 'spóźnienie', kind_ABSENT: 'nieobecny', kind_EXCUSED: 'usprawiedliwiony', kind_RELEASED: 'zwolniony',
    prevWeek: '← Poprzedni', nextWeek: 'Następny →', thisWeek: 'Ten tydzień', no: 'Nr', time: 'Godz.', subject: 'Przedmiot', room: 'Sala',
    status_CANCELLED: 'odwołana', status_SUBSTITUTION: 'zastępstwo', status_MOVED_IN: 'przeniesiona tutaj', status_MOVED_OUT: 'przeniesiona',
    allDay: 'cały dzień', due: 'Termin', assigned: 'Zadane', attachments: 'Załączniki',
    inbox: 'Odebrane', outbox: 'Wysłane', compose: 'Nowa wiadomość', reply: 'Odpowiedz', send: 'Wyślij', sent: 'Wiadomość wysłana.',
    recipients: 'Odbiorcy', topic: 'Temat', text: 'Treść', from: 'Od', to: 'Do', back: '← Wstecz', more: 'Więcej',
    markRead: 'Oznacz jako przeczytane', read: 'przeczytane', newBadge: 'nowe',
    notes: 'Uwagi', points: 'Zachowanie i punkty',
    feeds: 'Kalendarze iCal', feedHelp: 'Adres tylko do odczytu – wklej go w Google/Apple/Outlook jako subskrypcję kalendarza. Każdy, kto zna adres, widzi wydarzenia; użyj „Nowy adres”, aby unieważnić stary.',
    newFeed: 'Nowy kalendarz', name: 'Nazwa', resetToken: 'Nowy adres', delete: 'Usuń', save: 'Zapisz', copy: 'Kopiuj', copied: 'Skopiowano.',
    allChildren: 'wszystkie dzieci', types: 'Rodzaje wpisów', children: 'Dzieci',
    subscriptions: 'Podsumowania e-mail', subscriptionHelp: 'Podsumowania wysyłane są automatycznie wg harmonogramu. Wybierz adresy, częstotliwość i rodzaje wpisów.',
    email: 'E-mail', frequencies: 'Częstotliwość', DAILY: 'codziennie', WEEKLY: 'co tydzień', MONTHLY: 'co miesiąc',
    enabled: 'Włączone', add: 'Dodaj', sendTest: 'Wyślij teraz', preview: 'Podgląd', schedule: 'Harmonogram',
    refresh: 'Odśwież dane z Librusa', refreshed: 'Dane odświeżone.', confirmDelete: 'Na pewno usunąć?', confirmReset: 'Stary adres przestanie działać. Kontynuować?',
    error: 'Błąd', offline: 'Brak połączenia – pokazuję zapisane dane.', moduleUnavailable: 'Moduł niedostępny dla tego konta.',
    loading: 'Ładowanie…', positive: 'pozytywna', negative: 'negatywna', neutral: 'neutralna',
    descriptiveGrades: 'Oceny opisowe', skill: 'umiejętność', requirements: 'wymagania',
    justifications: 'Usprawiedliwienia', just_PENDING: 'oczekuje', just_ACCEPTED: 'zaakceptowane', just_REJECTED: 'odrzucone',
    justifiedAbsences: 'usprawiedliwionych nieobecności', sentOn: 'wysłane'
  },
  en: {
    menu: 'Menu', child: 'Child', language: 'Language',
    login: 'Sign in', username: 'User name', password: 'Password', loginFailed: 'Wrong user name or password (or too many attempts).',
    logout: 'Sign out',
    dashboard: 'Dashboard', grades: 'Grades', attendance: 'Attendance', timetable: 'Timetable', calendar: 'Calendar',
    homework: 'Homework', messages: 'Messages', announcements: 'Announcements', behaviour: 'Notes & behaviour',
    settings: 'Settings',
    today: 'Today', tomorrow: 'Tomorrow', lessonsOn: 'Lessons', upcoming: 'Coming up (7 days)', recent: 'Recent changes',
    luckyNumber: 'Lucky number', unread: 'Unread messages', nothing: 'Nothing here.',
    noChildren: 'No Librus accounts configured. Set LIBRUS_ACCOUNT_1_LOGIN and LIBRUS_ACCOUNT_1_PASSWORD.',
    average: 'Average', semester1: 'Semester 1', semester2: 'Semester 2', summaryGrades: 'Semester / final grades',
    weight: 'weight', category: 'category', teacher: 'teacher', comment: 'comment',
    presence: 'Presence', lessons: 'lessons', absences: 'Absences and lateness',
    kind_PRESENT: 'present', kind_LATE: 'late', kind_ABSENT: 'absent', kind_EXCUSED: 'excused', kind_RELEASED: 'released',
    prevWeek: '← Previous', nextWeek: 'Next →', thisWeek: 'This week', no: 'No.', time: 'Time', subject: 'Subject', room: 'Room',
    status_CANCELLED: 'cancelled', status_SUBSTITUTION: 'substitution', status_MOVED_IN: 'moved here', status_MOVED_OUT: 'moved',
    allDay: 'all day', due: 'Due', assigned: 'Assigned', attachments: 'Attachments',
    inbox: 'Inbox', outbox: 'Sent', compose: 'New message', reply: 'Reply', send: 'Send', sent: 'Message sent.',
    recipients: 'Recipients', topic: 'Subject', text: 'Message', from: 'From', to: 'To', back: '← Back', more: 'More',
    markRead: 'Mark as read', read: 'read', newBadge: 'new',
    notes: 'Notes', points: 'Behaviour and points',
    feeds: 'iCal calendars', feedHelp: 'Read-only address - add it to Google/Apple/Outlook as a subscribed calendar. Anyone with the address sees the events; use "New address" to revoke the old one.',
    newFeed: 'New calendar', name: 'Name', resetToken: 'New address', delete: 'Delete', save: 'Save', copy: 'Copy', copied: 'Copied.',
    allChildren: 'all children', types: 'Entry types', children: 'Children',
    subscriptions: 'E-mail summaries', subscriptionHelp: 'Summaries are sent automatically on schedule. Choose addresses, frequency and entry types.',
    email: 'E-mail', frequencies: 'Frequency', DAILY: 'daily', WEEKLY: 'weekly', MONTHLY: 'monthly',
    enabled: 'Enabled', add: 'Add', sendTest: 'Send now', preview: 'Preview', schedule: 'Schedule',
    refresh: 'Refresh data from Librus', refreshed: 'Data refreshed.', confirmDelete: 'Delete for sure?', confirmReset: 'The old address will stop working. Continue?',
    error: 'Error', offline: 'Offline - showing saved data.', moduleUnavailable: 'Module not available for this account.',
    loading: 'Loading…', positive: 'positive', negative: 'negative', neutral: 'neutral',
    descriptiveGrades: 'Descriptive grades', skill: 'skill', requirements: 'requirements',
    justifications: 'Absence justifications', just_PENDING: 'pending', just_ACCEPTED: 'accepted', just_REJECTED: 'rejected',
    justifiedAbsences: 'absences excused', sentOn: 'sent'
  }
};

const STORAGE_KEY = 'jlibrus.lang';
let current = readStored() || 'pl';

function readStored() {
  try {
    const value = localStorage.getItem(STORAGE_KEY);
    return value === 'pl' || value === 'en' ? value : null;
  } catch (e) {
    return null;
  }
}

export function lang() {
  return current;
}

export function setLang(value) {
  current = value === 'en' ? 'en' : 'pl';
  try {
    localStorage.setItem(STORAGE_KEY, current);
  } catch (e) {
    // private mode: keep the choice for this page only
  }
  document.documentElement.lang = current;
}

export function t(key) {
  return DICT[current][key] ?? DICT.pl[key] ?? key;
}

export function applyStaticLabels(root = document) {
  root.querySelectorAll('[data-i18n-label]').forEach((el) => el.setAttribute('aria-label', t(el.dataset.i18nLabel)));
}
