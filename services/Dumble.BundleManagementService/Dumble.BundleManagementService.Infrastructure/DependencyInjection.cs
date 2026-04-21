using CloudinaryDotNet;
using Dumble.BundleManagementService.Application.Contracts;
using Dumble.BundleManagementService.Application.Contracts.Repositories;
using Dumble.BundleManagementService.Infrastructure.Authentication;
using Dumble.BundleManagementService.Infrastructure.Blobs;
using Dumble.BundleManagementService.Infrastructure.Persistence.Data;
using Dumble.BundleManagementService.Infrastructure.Persistence.Repositories;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Options;

namespace Dumble.BundleManagementService.Infrastructure;

public static class DependencyInjection
{
    public static IServiceCollection AddInfrastructure(this IServiceCollection services, IConfiguration configuration)
    {
        var connectionString = configuration.GetConnectionString("DatabaseConnection");
        services.AddDbContext<BundleManagementDbContext>(opt => opt.UseSqlServer(connectionString));

        services.AddScoped<ILoggedInUserService, LoggedInUserService>();
        services.AddScoped<IFileService, CloudinaryFileService>();
        services.AddScoped(typeof(IGenericRepository<,>), typeof(GenericRepository<,>));
        
        
        services.Configure<CloudinarySettings>(cs =>
        {
            var section = configuration.GetSection("Cloudinary");
            cs.ApiKey = section["ApiKey"]!;
            cs.ApiSecret = section["ApiSecret"]!;
            cs.CloudName = section["CloudName"]!;
        });

        services.AddSingleton(sp =>
        {
            var config = sp.GetRequiredService<IOptions<CloudinarySettings>>().Value;

            var account = new Account(
                config.CloudName,
                config.ApiKey,
                config.ApiSecret
            );

            var cloudinary = new Cloudinary(account)
            {
                Api = { Secure = true }
            };

            return cloudinary;
        });
        
        return services;
    }
}