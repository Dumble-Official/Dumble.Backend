using Dumble.BundleManagementService.Application.Contracts;
using Dumble.BundleManagementService.Application.Contracts.Repositories;
using Dumble.BundleManagementService.Application.Features.Bundles.Commands.UpdateBundle;
using Dumble.BundleManagementService.Domain.BundleAggregate;
using Dumble.BundleManagementService.Domain.BundleAggregate.ValueObjects;
using MediatR;
using Microsoft.Extensions.Logging;

namespace Dumble.BundleManagementService.Application.Features.Bundles.Commands.DeleteBundle;

internal sealed class DeleteBundleCommandHandler(ILogger<UpdateBundleCommandHandler> logger, 
    IFileService fileService,
    IGenericRepository<Bundle,BundleId> bundlesRepository, 
    ILoggedInUserService loggedInUserService) : IRequestHandler<DeleteBundleCommand>
{
    public async Task Handle(DeleteBundleCommand request, CancellationToken cancellationToken)
    {
        var loggedInUser = loggedInUserService.GetCurrentUser();

        var bundle = await bundlesRepository.Get(BundleId.Create(request.Id));
        
        if (bundle is null) throw new Exception("Not Found Exception!");

        var currentUserAccountId = AccountId.Create(loggedInUser.Id);
        
        if (!currentUserAccountId.Equals(bundle.OwnerId))
            throw new Exception("UnAuthorized");
        
        var tasks = bundle.Images.Select(img => fileService.DeleteAsync(img.Value));

        var results = await Task.WhenAll(tasks);
        
        bundlesRepository.Delete(bundle);

        var rowsAffected = await bundlesRepository.CompleteAsync();
    }
}