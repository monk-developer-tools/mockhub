export const MAX_FIELDS = 20;
export const SEPARATOR = '\u001e';
const MAX_DEPTH = 3;

const BY_NAME = [
  [/^(e?mail|emailaddress)$/, 'Internet.emailAddress'],
  [/^(firstname|givenname|forename)$/, 'Name.firstName'],
  [/^(lastname|surname|familyname)$/, 'Name.lastName'],
  [/^(fullname|name|customername|displayname|author|contactname)$/, 'Name.fullName'],
  [/^(username|login|handle|nickname)$/, 'Internet.username'],
  [/^(phone|phonenumber|mobile|tel|telephone|cell)$/, 'PhoneNumber.phoneNumber'],
  [/^(jobtitle|job|position|role|occupation)$/, 'Job.title'],
  [/^(street|streetaddress|address|addressline1|line1)$/, 'Address.streetAddress'],
  [/^(city|town)$/, 'Address.city'],
  [/^(zip|zipcode|postcode|postalcode)$/, 'Address.zipCode'],
  [/^(state|region|province|county)$/, 'Address.state'],
  [/^(country|countryname)$/, 'Address.country'],
  [/^(lat|latitude)$/, 'Address.latitude'],
  [/^(lng|lon|long|longitude)$/, 'Address.longitude'],
  [/^(company|companyname|organisation|organization|employer)$/, 'Company.name'],
  [/^(product|productname|item|itemname)$/, 'Commerce.productName'],
  [/^(department|category)$/, 'Commerce.department'],
  [/^(iban)$/, 'Finance.iban'],
  [/^(currency|currencycode)$/, 'Money.currencyCode'],
  [/^(colou?r)$/, 'Color.name'],
  [/^(url|website|homepage|link|avatar|image|imageurl|photo|picture)$/, 'Internet.url'],
  [/^(title|booktitle|headline|subject)$/, 'Book.title'],
  [/^(description|body|text|comment|bio|summary|notes?|message|content)$/, 'Lorem.sentence'],
];

const DATE = /^\d{4}-\d{2}-\d{2}$/;
const DATETIME = /^\d{4}-\d{2}-\d{2}[T ]\d{2}:\d{2}/;
const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const URL = /^https?:\/\//i;
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

const plain = (key) => key.toLowerCase().replace(/[^a-z0-9]/g, '');

function numberRange(value) {
  const top = Math.max(10, Math.min(1000, 10 ** Math.max(1, String(Math.trunc(Math.abs(value))).length)));
  return `'${value < 0 ? -top : 0}','${top}'`;
}

export function expressionFor(name, value) {
  const key = plain(name.split('.').pop());
  if (typeof value === 'boolean') return 'Bool.bool';
  if (key === 'id') return typeof value === 'string' && UUID.test(value) ? 'uuid' : 'sequence';
  if (typeof value === 'string' && UUID.test(value)) return 'uuid';
  if (/(price|amount|total|cost|balance|salary|fee)$/.test(key) && typeof value === 'number') return "Number.randomDouble '2','1','1000'";
  if (/(birthday|birthdate|dob|dateofbirth)$/.test(key)) return "Date.birthday 'yyyy-MM-dd'";
  if (typeof value === 'string' && DATETIME.test(value)) return "TimeAndDate.past '365','DAYS'";
  if (typeof value === 'string' && DATE.test(value)) return "Date.birthday 'yyyy-MM-dd'";
  for (const [pattern, expression] of BY_NAME) {
    if (pattern.test(key)) return expression;
  }
  if (typeof value === 'number') return Number.isInteger(value) ? `Number.numberBetween ${numberRange(value)}` : "Number.randomDouble '2','1','1000'";
  if (typeof value === 'string' && EMAIL.test(value)) return 'Internet.emailAddress';
  if (typeof value === 'string' && URL.test(value)) return 'Internet.url';
  if (typeof value === 'string' && value.trim().split(/\s+/).length > 3) return 'Lorem.sentence';
  return 'Lorem.word';
}

export function inferFields(sample) {
  const record = Array.isArray(sample) ? sample[0] : sample;
  if (record === null || typeof record !== 'object' || Array.isArray(record)) {
    throw new Error('Paste one JSON object, like {"name": "Ada", "email": "ada@example.com"}, or a list of them.');
  }
  const fields = [];
  const notes = [];
  const walk = (node, prefix, depth) => {
    for (const [key, value] of Object.entries(node)) {
      const name = prefix ? `${prefix}.${key}` : key;
      if (!/^[A-Za-z_][A-Za-z0-9_]*$/.test(key)) {
        notes.push(`${name}: field names are letters, digits and underscores`);
      } else if (Array.isArray(value)) {
        notes.push(`${name}: lists aren't made from fields; use a template for them`);
      } else if (value !== null && typeof value === 'object') {
        if (depth >= MAX_DEPTH) notes.push(`${name}: nested more than ${MAX_DEPTH} levels deep`);
        else walk(value, name, depth + 1);
      } else if (fields.length >= MAX_FIELDS) {
        notes.push(`${name}: only the first ${MAX_FIELDS} fields are used`);
      } else {
        fields.push({ name, expression: expressionFor(name, value) });
      }
    }
  };
  walk(record, '', 1);
  if (!fields.length) throw new Error('No fields to imitate in that sample.');
  return { fields, notes };
}

export function templateWrapper(template, count) {
  return `{{#each (range 1 ${count})}}${SEPARATOR}${template}{{/each}}`;
}

export function splitRendered(output) {
  const parts = String(output ?? '').split(SEPARATOR).slice(1);
  return parts.map((text, i) => {
    try {
      return JSON.parse(text);
    } catch (e) {
      throw new Error(`Record ${i + 1} isn't valid JSON: ${e.message}. The template must make one JSON object per record.`);
    }
  });
}
