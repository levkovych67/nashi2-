/** True for an http(s) address: the only links from the API that may go into an href. */
export const isWebUrl = (url: string | null | undefined): url is string => !!url && /^https?:\/\//i.test(url);
