import { Upload } from 'lucide-react';
import { useRef, useState } from 'react';

interface Props {
  accept: string;
  label: string;
  onText: (text: string, fileName: string) => void;
}

export function FilePicker({ accept, label, onText }: Props) {
  const input = useRef<HTMLInputElement>(null);
  const [over, setOver] = useState(false);
  const read = (file?: File) => file && file.text().then((t) => onText(t, file.name));
  return (
    <div
      className={`dropzone${over ? ' over' : ''}`}
      role="button"
      tabIndex={0}
      onClick={() => input.current?.click()}
      onKeyDown={(e) => (e.key === 'Enter' || e.key === ' ') && input.current?.click()}
      onDragOver={(e) => {
        e.preventDefault();
        setOver(true);
      }}
      onDragLeave={() => setOver(false)}
      onDrop={(e) => {
        e.preventDefault();
        setOver(false);
        read(e.dataTransfer.files[0]);
      }}
    >
      <Upload size={18} />
      <div>{label}</div>
      <input ref={input} type="file" accept={accept} hidden onChange={(e) => read(e.target.files?.[0])} />
    </div>
  );
}
