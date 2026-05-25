using CloudinaryDotNet;
using Dumble.BundleManagementService.Application.Contracts;
using Dumble.BundleManagementService.Application.Contracts.Repositories;
using Dumble.BundleManagementService.Infrastructure.Authentication;
using Dumble.BundleManagementService.Infrastructure.Blobs;
using Dumble.BundleManagementService.Infrastructure.Persistence.Data;
using Dumble.BundleManagementService.Infrastructure.Persistence.Repositories;
using Dumble.BundleManagementService.Infrastructure.Services;
using Dumble.SharedKernel.Contracts;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Options;

namespace Dumble.BundleManagementService.Infrastructure;

public static class DependencyInjection
{
    public static IServiceCollection AddInfrastructure(this IServiceCollection services, IConfiguration configuration)
    {
        var connectionString = configuration.GetConnectionString("DatabaseConnection")
            ?? throw new InvalidOperationException("ConnectionStrings:DatabaseConnection is required");
        services.AddDbContext<BundleManagementDbContext>(opt => opt.UseSqlServer(connectionString));

        services.AddScoped<ILoggedInUserService, LoggedInUserService>();
        services.AddScoped(typeof(IGenericRepository<,>), typeof(GenericRepository<,>));
        services.AddScoped<IAdminActionRepository, AdminActionRepository>();
        services.Configure<AdminBypassRateLimiterOptions>(configuration.GetSection("AdminBypassRateLimiter"));
        services.AddSingleton<IAdminBypassRateLimiter, InMemoryAdminBypassRateLimiter>();

        services.Configure<CloudinarySettings>(cs =>
        {
            var section = configuration.GetSection("Cloudinary");
            cs.CloudName = section["CloudName"]
                ?? throw new InvalidOperationException("Cloudinary:CloudName is required");
            cs.ApiKey = section["ApiKey"]
                ?? throw new InvalidOperationException("Cloudinary:ApiKey is required");
            cs.ApiSecret = section["ApiSecret"]
                ?? throw new InvalidOperationException("Cloudinary:ApiSecret is required");
        });

        services.AddSingleton(sp =>
        {
            var config = sp.GetRequiredService<IOptions<CloudinarySettings>>().Value;
            var account = new Account(config.CloudName, config.ApiKey, config.ApiSecret);
            return new Cloudinary(account) { Api = { Secure = true } };
        });

        services.AddHttpClient();
        services.AddScoped<IFileService, CloudinaryFileService>();

        return services;
    }
}
