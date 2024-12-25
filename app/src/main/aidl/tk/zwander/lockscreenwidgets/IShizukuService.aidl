package tk.zwander.lockscreenwidgets;

interface IShizukuService {
    void grantReadExternalStorage() = 1;

    void destroy() = 16777114;
}