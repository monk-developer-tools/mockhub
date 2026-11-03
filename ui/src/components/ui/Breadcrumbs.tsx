import { CaretRight } from '@phosphor-icons/react';
import { Fragment } from 'react';
import { Link } from 'react-router-dom';
import type { Crumb } from '../../model/navigation';

export function Breadcrumbs({ crumbs }: { crumbs: Crumb[] }) {
  return (
    <nav className="breadcrumbs" aria-label="Breadcrumb">
      <ol>
        {crumbs.map((c, i) => {
          const last = i === crumbs.length - 1;
          return (
            <Fragment key={`${c.label}-${i}`}>
              <li>
                {c.to && !last ? (
                  <Link to={c.to}>{c.label}</Link>
                ) : (
                  <span aria-current={last ? 'page' : undefined}>{c.label}</span>
                )}
              </li>
              {!last && (
                <li className="breadcrumb-sep" aria-hidden="true">
                  <CaretRight size={12} />
                </li>
              )}
            </Fragment>
          );
        })}
      </ol>
    </nav>
  );
}
