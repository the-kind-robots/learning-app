// A device with an account, for specs that need one. The example fetcher asks
// nothing of a device without an account (the endpoint answers it 401), and
// the suite's backend has no CouchDB to provision one against.
//
// The account is an identity written into device-db, as an adopted key leaves
// it, and the account's copy on the server is a stub that holds nothing: every
// replication pass completes, pulls nothing and accepts what it pushes. Only
// the protocol calls PouchDB makes are answered.

const STUB_DB = { db_name: 'userdb-stub', update_seq: '0', instance_start_time: '0' };

async function stubAccountCopy(context) {
  await context.route('**/db/**', async (route) => {
    const request = route.request();
    const path = new URL(request.url()).pathname;
    if (path.includes('/_local/')) {
      return request.method() === 'GET'
        ? route.fulfill({ status: 404, json: { error: 'not_found', reason: 'missing' } })
        : route.fulfill({ status: 201, json: { ok: true, id: path.split('/_local/')[1], rev: '0-1' } });
    }
    if (path.endsWith('/_changes')) return route.fulfill({ json: { results: [], last_seq: '0' } });
    if (path.endsWith('/_revs_diff')) return route.fulfill({ json: {} });
    if (path.endsWith('/_bulk_docs')) return route.fulfill({ status: 201, json: [] });
    if (path.endsWith('/_bulk_get')) return route.fulfill({ json: { results: [] } });
    return route.fulfill({ json: STUB_DB });
  });
}

// Gives the page's device an account and reloads it, so the start reads it.
async function becomeAccount(page) {
  await stubAccountCopy(page.context());
  await page.evaluate(async () => {
    const device = db.use('device-db');
    const stored = await device.get('identity:local').catch(() => null);
    if (!stored) {
      await device.put({ _id: 'identity:local', type: 'identity', user_id: 1, token: 'stub-token' });
    }
  });
  await page.reload();
}

module.exports = { becomeAccount, stubAccountCopy };
