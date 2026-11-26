export const CLAUSE_TYPE_LABEL: Record<string, string> = {
  PAYMENT: "Оплата",
  TERM: "Срок",
  TERMINATION: "Расторжение",
  LIABILITY: "Ответственность",
  LIABILITY_LIMIT: "Ограничение ответственности",
  CONFIDENTIALITY: "Конфиденциальность",
  DISPUTES: "Споры",
  FORCE_MAJEURE: "Форс-мажор",
  AMENDMENT: "Изменение договора",
  WARRANTY: "Гарантии",
  PERSONAL_DATA: "Персональные данные",
  SUBJECT: "Предмет договора",
  WORK_CONDITIONS: "Условия труда",
  OTHER: "Прочее",
};

export const CLAUSE_TYPES = Object.keys(CLAUSE_TYPE_LABEL);

export const MODALITY_LABEL: Record<string, string> = {
  RIGHT: "право",
  OBLIGATION: "обязанность",
  PROHIBITION: "запрет",
};

export const MODALITY_COLOR: Record<string, string> = {
  RIGHT: "var(--color-success)",
  OBLIGATION: "var(--color-info)",
  PROHIBITION: "var(--color-error)",
};

export const PARTIES = [
  "Работодатель",
  "Работник",
  "Арендодатель",
  "Арендатор",
  "Наймодатель",
  "Наниматель",
  "Заказчик",
  "Исполнитель",
  "Подрядчик",
  "Поставщик",
  "Покупатель",
  "Продавец",
  "Займодатель",
  "Заемщик",
  "Стороны",
];
