using Dumble.BundleManagementService.Application.Contracts;
using Dumble.BundleManagementService.Application.Contracts.Repositories;
using Dumble.BundleManagementService.Domain.BundleAggregate;
using Dumble.BundleManagementService.Domain.BundleAggregate.ValueObjects;
using MediatR;
using Microsoft.Extensions.Logging;

namespace Dumble.BundleManagementService.Application.Features.Bundles.Commands.UpdateBundle;

internal sealed class UpdateBundleCommandHandler(ILogger<UpdateBundleCommandHandler> logger, 
    IFileService fileService,
    IGenericRepository<Bundle,BundleId> bundlesRepository, 
    ILoggedInUserService loggedInUserService) : IRequestHandler<UpdateBundleCommand>
{
    public async Task Handle(UpdateBundleCommand request, CancellationToken cancellationToken)
    {
        var loggedInUser = loggedInUserService.GetCurrentUser();

        var bundle = await bundlesRepository.Get(BundleId.Create(request.Id));
        
        if (bundle is null) throw new Exception("Not Found Exception!");

        var currentUserAccountId = AccountId.Create(loggedInUser.Id);
        
        if (!currentUserAccountId.Equals(bundle.OwnerId))
            throw new Exception("UnAuthorized");
        
        // Update Bundle Data.
        
        
        bundlesRepository.Update(bundle);

        var rowsAffected = await bundlesRepository.CompleteAsync();
    }
}